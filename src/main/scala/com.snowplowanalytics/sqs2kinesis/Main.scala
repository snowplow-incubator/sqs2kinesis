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

object Main extends App with LazyLogging {

  implicit val system: ActorSystem = ActorSystem()

  val sqsEndpoint = "http://localhost:4576"
  val sqsQueue    = "http://localhost:4576/queue/good-events-queue"

  val kinesisEndpoint   = "http://localhost:4568/"
  val kinesisStreamName = "good-events"

  val region = Region.EU_CENTRAL_1

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
      println("ERROR in stream >>>>>>>>> ", e.getMessage())
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

  // kinesis - consumer

  import akka.stream.alpakka.kinesis.ShardSettings
  // import com.amazonaws.services.kinesis.model.ShardIteratorType
  import akka.stream.alpakka.kinesis.scaladsl.KinesisSource

  val settings =
    ShardSettings(streamName = "good-events", shardId = "shardId-000000000000")
      .withRefreshInterval(1.second)
      .withLimit(500)
      // .withShardIteratorType(ShardIteratorType.AT_SEQUENCE_NUMBER)
      .withStartingAfterSequenceNumber("49604123340698769188642183422668174591051070687106039810")

  val kinesisSource: Source[com.amazonaws.services.kinesis.model.Record, NotUsed] =
    KinesisSource.basic(settings, kinesisClient)

  val kinesisConsumer =
    kinesisSource.zipWithIndex.runWith(Sink.foreach {
      case (x, idx) =>
        println(s"consumed ${idx + 1}> ${x.getSequenceNumber}")
    })

  // kinesis - consumer

}
