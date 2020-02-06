/*
 * Copyright (c) 2020-2020 Snowplow Analytics Ltd. All rights reserved.
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
import software.amazon.awssdk.services.sqs.SqsAsyncClient
import software.amazon.awssdk.services.sqs.model.Message
import com.amazonaws.services.kinesis.{AmazonKinesisAsync, AmazonKinesisAsyncClientBuilder}
import com.amazonaws.services.kinesis.model.PutRecordsResultEntry
import com.amazonaws.services.kinesis.model.PutRecordsRequestEntry
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
import akka.stream.ActorAttributes
import akka.stream.Supervision
import scala.concurrent.duration._
import com.typesafe.scalalogging.Logger
import java.util.UUID
import akka.stream.alpakka.sqs.MessageAttributeName
import java.io.PrintStream
import java.io.ByteArrayOutputStream

object EventsStreamModule {

  case class StreamConfig(
    sqsQueue: String,
    kinesisStreamName: String
  )

  private val logger = Logger[EventsStreamModule.type]

  def runStream(config: StreamConfig)(implicit system: ActorSystem) = {

    implicit val sqsClient: SqsAsyncClient = {
      val client = SqsAsyncClient.builder().httpClient(AkkaHttpClient.builder().withActorSystem(system).build()).build()

      system.registerOnTermination(client.close())
      client
    }

    implicit val kinesisClient: AmazonKinesisAsync = {
      val client = AmazonKinesisAsyncClientBuilder.standard().build()

      system.registerOnTermination(client.shutdown())
      client
    }

    val KinesisKey = "kinesisKey"

    val sqsSource: Source[Message, NotUsed] =
      SqsSource(
        config.sqsQueue,
        SqsSourceSettings.Defaults.withMessageAttribute(MessageAttributeName(KinesisKey))
      )

    type KinesisKeyAndMsg = (String, ByteBuffer)

    val sqsMsg2kinesisMsg: Message => KinesisKeyAndMsg =
      msg => {
        val decoded  = ByteBuffer.wrap(java.util.Base64.getDecoder.decode(msg.body))
        val maybeKey = Option(msg.messageAttributes().get(KinesisKey)).map(_.stringValue())
        val key = maybeKey.getOrElse {
          val randomKey = UUID.randomUUID().toString
          logger.warn(s"Kinesis key for sqs message ${msg.messageId()} not found, random key generated: $randomKey")
          randomKey
        }
        (key, decoded)
      }

    val toPutRecordReqEntry: KinesisKeyAndMsg => PutRecordsRequestEntry = {
      case (key, data) =>
        new PutRecordsRequestEntry().withPartitionKey(key).withData(data)
    }

    def stackTraceToString(e: Throwable): String = {
      val out = new ByteArrayOutputStream()
      val ps  = new PrintStream(out)
      e.printStackTrace(ps)
      val stack = out.toString
      ps.close
      out.close
      stack
    }

    val kinesisFlow: Flow[(PutRecordsRequestEntry, Message), (PutRecordsResultEntry, Message), NotUsed] =
      RestartFlow.withBackoff(500.milli, 1.second, 0.2) { () =>
        KinesisFlow
          .withUserContext(
            config.kinesisStreamName,
            KinesisFlowSettings.Defaults.withMaxBatchSize(500).withMaxRetries(10)
          )
          .recover {
            case e =>
              val stackTrace = stackTraceToString(e)
              logger.error(
                s"Excpetion in kinesis flow. Message: ${e.getMessage}, stack: $stackTrace",
                e
              )
              throw e
          }
      }

    val confirmSqsSink: Sink[Message, NotUsed] =
      Flow[Message].map(MessageAction.Delete(_)).to(SqsAckSink(config.sqsQueue))

    val decider: Supervision.Decider = {
      case e @ _ => {
        val stackTrace = stackTraceToString(e)
        logger.error(s"Error in stream: $stackTrace", e)
        Supervision.Resume
      }
    }

    def printProgress[T]: Sink[T, NotUsed] =
      Flow[T]
        .groupedWithin(10000, 1.minute)
        .map(msg => s"sqs2kinesis processed ${msg.length} messages")
        .to(Sink.foreach(logger.info(_)))

    sqsSource
      .asSourceWithContext(m => m)
      .map(sqsMsg2kinesisMsg)
      .map(toPutRecordReqEntry)
      .via(kinesisFlow)
      .asSource
      .map(_._2)
      .alsoTo(confirmSqsSink)
      .alsoTo(printProgress)
      .toMat(Sink.ignore)(Keep.right)
      .withAttributes(ActorAttributes.supervisionStrategy(decider))
      .run()
  }
}
