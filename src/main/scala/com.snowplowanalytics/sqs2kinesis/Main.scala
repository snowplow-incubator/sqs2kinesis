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

object Main extends App {

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

  val kinesisFlow: Flow[(String, ByteBuffer), PutRecordsResultEntry, NotUsed] =
    KinesisFlow
      .byPartitionAndData(
        kinesisStreamName,
        KinesisFlowSettings.Defaults
      )

  type KinesisKeyAndMsg = (String, ByteBuffer)

  val sqsMsg2kinesisMsg: Flow[Message, KinesisKeyAndMsg, NotUsed] =
    Flow[Message].map { m =>
      val decoded    = java.util.Base64.getDecoder().decode(m.body)
      val (key, msg) = decoded.splitAt(decoded.indexOf('|'.toByte))
      (new String(key), ByteBuffer.wrap(msg))
    }

  val printMsg: Flow[(Message, Long), Message, NotUsed] =
    Flow[(Message, Long)].map {
      case (m, idx) =>
        println(s"$idx> ${m.body().substring(0, 50)}")
        m
    }

  def confirmSqsSink: Sink[Message, NotUsed] =
    Flow[Message].map(MessageAction.Delete(_)).to(SqsAckSink(sqsQueue))

  sqsSource.zipWithIndex //todo: remove index and printing msg to stdout
    .via(printMsg)
    .alsoTo(confirmSqsSink) //todo: confirm after successfull send to Kinesis
    .via(sqsMsg2kinesisMsg)
    .via(kinesisFlow)
    .runWith(Sink.ignore)

}
