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

import scala.concurrent.duration._
import com.amazonaws.services.kinesis.AmazonKinesisAsyncClientBuilder
import com.amazonaws.services.kinesis.model.PutRecordsRequestEntry
import com.github.matsluni.akkahttpspi.AkkaHttpClient
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.sqs.SqsAsyncClient
import java.net.URI
import akka.actor.ActorSystem
import akka.stream.scaladsl._
import akka.stream.alpakka.sqs.scaladsl.SqsSource
import akka.stream.alpakka.sqs.SqsSourceSettings
import akka.stream.scaladsl.Sink
import akka.stream.alpakka.kinesis.KinesisFlowSettings
import akka.NotUsed
import akka.stream.alpakka.kinesis.scaladsl.KinesisSink
import software.amazon.awssdk.services.sqs.model.Message
import java.nio.ByteBuffer
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain
import com.amazonaws.client.builder.AwsClientBuilder.EndpointConfiguration
import com.amazonaws.services.kinesis.model.PutRecordsResultEntry
import akka.stream.alpakka.kinesis.scaladsl.KinesisFlow
import akka.stream.alpakka.sqs.MessageAction
import akka.stream.alpakka.sqs.SqsAckSettings
import akka.stream.alpakka.sqs.SqsAckResult
import akka.stream.alpakka.sqs.scaladsl.SqsAckSink
import scala.concurrent.Future
import akka.Done
import com.amazonaws.services.kinesis.model.PutRecordRequest
import com.amazonaws.services.kinesis.model.PutRecordsRequest

object Main extends App {

  implicit val system: ActorSystem = ActorSystem()

  val sqsEndpoint = "http://localhost:4576"
  val sqsQueue    = "http://localhost:4576/queue/good-events-queue"

  val kinesisEndpoint   = "http://localhost:4568/"
  val kinesisStreamName = "good-events"

  val region = Region.EU_CENTRAL_1

  implicit val sqsClient = SqsAsyncClient
    .builder()
    .credentialsProvider(DefaultCredentialsProvider.create())
    .endpointOverride(URI.create(sqsEndpoint))
    .region(region)
    .httpClient(AkkaHttpClient.builder().withActorSystem(system).build())
    .build()

  system.registerOnTermination(sqsClient.close())

  implicit val kinesisClient: com.amazonaws.services.kinesis.AmazonKinesisAsync =
    AmazonKinesisAsyncClientBuilder
      .standard()
      .withCredentials(new DefaultAWSCredentialsProviderChain())
      .withEndpointConfiguration(new EndpointConfiguration(kinesisEndpoint, region.toString()))
      .build()

  system.registerOnTermination(kinesisClient.shutdown())

  // val flowSettings = KinesisFlowSettings
  //   .create()
  //   .withParallelism(1)
  //   .withMaxBatchSize(500)
  //   .withMaxRecordsPerSecond(1000)
  //   .withMaxBytesPerSecond(1000000)
  //   .withMaxRetries(5)
  //   .withBackoffStrategy(KinesisFlowSettings.Exponential)
  //   .withRetryInitialTimeout(100.milli)

  val flowSettings = KinesisFlowSettings.Defaults

  val sqsSource: Source[Message, NotUsed] =
    SqsSource(sqsQueue, SqsSourceSettings().withWaitTime(10.millis))

  val kinesisSink: Sink[(String, ByteBuffer), NotUsed] =
    KinesisSink.byPartitionAndData(kinesisStreamName, flowSettings)

  type KinesisKeyAndMsg = (String, ByteBuffer)

  private val sqsMsg2kinesisMsg: Flow[Message, KinesisKeyAndMsg, NotUsed] =
    Flow[Message].map { m =>
      val decoded    = java.util.Base64.getDecoder().decode(m.body)
      val (key, msg) = decoded.splitAt(decoded.indexOf('|'.toByte))
      (new String(key), ByteBuffer.wrap(msg))
    }

  val printMsg: Flow[Message, Message, NotUsed] =
    Flow[Message].map { m =>
      println(s"> ${m.body()}")
      m
    }

  val printSent: Flow[KinesisKeyAndMsg, KinesisKeyAndMsg, NotUsed] =
    Flow[KinesisKeyAndMsg].map { m =>
      println(" < sent to kinesis")
      m
    }

  def confirmSqsSink: Sink[Message, NotUsed] =
    Flow[Message].map(MessageAction.Delete(_)).to(SqsAckSink(sqsQueue))

  sqsSource
    .via(printMsg)
    .alsoTo(confirmSqsSink)
    .via(sqsMsg2kinesisMsg)
    .via(printSent)
    .runWith(kinesisSink)

}
