/*
 * Copyright (c) 2020 Snowplow Analytics Ltd. All rights reserved.
 *
 * This program is licensed to you under the Apache License Version 2.0,
 * and you may not use this file except in compliance with the Apache License Version 2.0.
 * You may obtain a copy of the Apache License Version 2.0 at http://www.apache.org/licenses/LICENSE-2.0.
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the Apache License Version 2.0 is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Apache License Version 2.0 for the specific language governing permissions and limitations there under.
 */

package com.snowplowanalytics.sqs2kinesis

import com.github.matsluni.akkahttpspi.AkkaHttpClient
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.sqs.SqsAsyncClient
import software.amazon.awssdk.services.sqs.model.Message
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider
import java.net.URI
import java.nio.ByteBuffer
import akka.actor.ActorSystem
import akka.stream.scaladsl._
import akka.stream.alpakka.sqs.scaladsl.SqsSource
import akka.stream.alpakka.sqs.SqsSourceSettings
import akka.stream.alpakka.kinesis.KinesisFlowSettings
import akka.stream.alpakka.kinesis.scaladsl.KinesisFlow
import akka.stream.alpakka.sqs.MessageAction
import akka.stream.alpakka.sqs.scaladsl.SqsAckSink
import akka.NotUsed
import com.amazonaws.services.kinesis.AmazonKinesisAsyncClientBuilder
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain
import com.amazonaws.client.builder.AwsClientBuilder.EndpointConfiguration
import com.amazonaws.services.kinesis.model.PutRecordsResultEntry
import akka.stream.ActorAttributes
import akka.stream.Supervision
import com.typesafe.scalalogging.LazyLogging
import com.amazonaws.services.kinesis.model.PutRecordsRequestEntry
import scala.concurrent.duration._
import com.typesafe.config.ConfigFactory

object Main extends App with LazyLogging {

  val appConfig = ConfigFactory.load().getConfig("sqs2kinesis")

  val sqsEndpoint       = appConfig.getString("sqs-endpoint")
  val sqsQueue          = appConfig.getString("sqs-queue")
  val kinesisEndpoint   = appConfig.getString("kinesis-endpoint")
  val kinesisStreamName = appConfig.getString("kinesis-stream-name")

  logger.info(s"config: $appConfig")

  implicit val system: ActorSystem = ActorSystem()
  val region                       = Region.EU_CENTRAL_1

  implicit val sqsClient = {
    val client = SqsAsyncClient
      .builder()
      .credentialsProvider(DefaultCredentialsProvider.create())
      .endpointOverride(URI.create(sqsEndpoint))
      .region(region)
      .httpClient(AkkaHttpClient.builder().withActorSystem(system).build())
      .build()

    system.registerOnTermination(client.close())
    client
  }

  implicit val kinesisClient = {
    val client = AmazonKinesisAsyncClientBuilder
      .standard()
      .withCredentials(new DefaultAWSCredentialsProviderChain())
      .withEndpointConfiguration(new EndpointConfiguration(kinesisEndpoint, region.toString()))
      .build()

    system.registerOnTermination(client.shutdown())
    client
  }

  val sqsSource: Source[Message, NotUsed] =
    SqsSource(sqsQueue, SqsSourceSettings.Defaults)

  type KinesisKeyAndMsg = (String, ByteBuffer)

  val toPutRecordReqEntry: ((String, ByteBuffer)) => PutRecordsRequestEntry = {
    case (key, data) =>
      new PutRecordsRequestEntry()
        .withPartitionKey(key)
        .withData(data)
  }

  val kinesisFlow
    : Flow[(PutRecordsRequestEntry, Message), (PutRecordsResultEntry, Message), NotUsed] =
    RestartFlow.withBackoff(500.milli, 1.second, 0.2) { () =>
      KinesisFlow
        .withUserContext(
          kinesisStreamName,
          KinesisFlowSettings.Defaults.withMaxBatchSize(500).withMaxRetries(10)
        )
    }

  val sqsMsg2kinesisMsg: Message => KinesisKeyAndMsg =
    m => {
      val decoded    = java.util.Base64.getDecoder().decode(m.body)
      val (key, msg) = decoded.splitAt(decoded.indexOf('|'.toByte))
      (new String(key), ByteBuffer.wrap(msg))
    }

  val printMsg: Flow[(Message, Long), Message, NotUsed] =
    Flow[(Message, Long)].map {
      case (m, idx) =>
        println(s"from sqs ${idx + 1}> ${m.body().substring(0, 50)}")
        m
    }

  def confirmSqsSink: Sink[Message, NotUsed] =
    Flow[Message]
      .map(MessageAction.Delete(_))
      .to(SqsAckSink(sqsQueue))

  val decider: Supervision.Decider = {
    case e @ _ => {
      logger.error("ERROR in stream", e)
      Supervision.Resume
    }
  }

  implicit val ec: scala.concurrent.ExecutionContext = scala.concurrent.ExecutionContext.global

  sqsSource.zipWithIndex //todo: remove index and printing msg to stdout
    .via(printMsg)
    .asSourceWithContext(m => m)
    .map(sqsMsg2kinesisMsg)
    .map(toPutRecordReqEntry)
    .via(kinesisFlow)
    .asSource
    .alsoTo(Sink.foreach { case (r, _) => println(r) })
    .map(_._2)
    .alsoTo(confirmSqsSink)
    .toMat(Sink.ignore)(Keep.right)
    .withAttributes(ActorAttributes.supervisionStrategy(decider))
    .run()
    .onComplete(println)

}
