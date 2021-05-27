/*
 * Copyright (c) 2020-2021 Snowplow Analytics Ltd. All rights reserved.
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

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.scaladsl._
import akka.stream.alpakka.sqs.scaladsl.{SqsAckFlow, SqsSource}
import akka.stream.alpakka.sqs.{MessageAction, MessageAttributeName, SqsSourceSettings}
import akka.stream.RestartSettings
import cats.data.NonEmptyList
import cats.syntax.either._
import com.github.matsluni.akkahttpspi.AkkaHttpClient
import com.typesafe.scalalogging.Logger
import software.amazon.awssdk.core.SdkBytes
import software.amazon.awssdk.services.kinesis.KinesisAsyncClient
import software.amazon.awssdk.services.kinesis.model.{PutRecordsRequest, PutRecordsRequestEntry}
import software.amazon.awssdk.services.sqs.SqsAsyncClient
import software.amazon.awssdk.services.sqs.model.Message

import com.snowplowanalytics.snowplow.badrows.{BadRow, Failure, Payload, Processor}

import java.util.UUID
import java.time.Instant
import java.nio.charset.StandardCharsets.UTF_8

import scala.concurrent.duration.DurationLong
import scala.concurrent.ExecutionContext
import scala.jdk.CollectionConverters._
import scala.jdk.FutureConverters._
import scala.util.control.NonFatal
import scala.util.Random

object EventsStreamModule {

  private val logger = Logger[EventsStreamModule.type]

  val processor: Processor = Processor(generated.BuildInfo.name, generated.BuildInfo.version)

  def runStream(config: Sqs2KinesisConfig)(implicit system: ActorSystem) = {

    val httpClient = AkkaHttpClient.builder().withActorSystem(system).build()

    implicit val executionContext: ExecutionContext = system.dispatcher

    implicit val sqsClient: SqsAsyncClient = {
      val client = SqsAsyncClient.builder().httpClient(httpClient).build()
      system.registerOnTermination(client.close())
      client
    }

    val kinesisClient: KinesisAsyncClient = {
      val client = KinesisAsyncClient.builder().httpClient(httpClient).build()
      system.registerOnTermination(client.close())
      client
    }

    val goodSink =
      Batcher
        .batch[ParsedMsg](
          keepAlive = 1.second,
          maxSize   = MaxKinesisBatch,
          maxWeight = MaxKinesisBytesPerRequest,
          toWeight = { m =>
            m.bytes.size + m.key.getBytes.size
          }
        )
        .via(kinesisFlow(config.goodStreamName, kinesisClient))
        .to(confirmSqsSink(config))

    val badSink =
      Batcher
        .batch[ParsedMsg](
          keepAlive = 1.second,
          maxSize   = MaxKinesisBatch,
          maxWeight = MaxKinesisBytesPerRequest,
          toWeight = { m =>
            m.bytes.size + m.key.getBytes.size
          }
        )
        .via(kinesisFlow(config.badStreamName, kinesisClient))
        .to(confirmSqsSink(config))

    sqsSource(config)
      .via(sqsMsg2kinesisMsg)
      .alsoTo(Flow[Either[ParsedMsg, ParsedMsg]].mapConcat(_.toSeq).to(goodSink))
      .alsoTo(Flow[Either[ParsedMsg, ParsedMsg]].mapConcat(_.left.toSeq).to(badSink))
      .to(printProgress)
      .run()
  }

  val KinesisKey = "kinesisKey"

  /** A source that reads messages from sqs and retries if read was unsuccessful */
  def sqsSource(config: Sqs2KinesisConfig)(implicit client: SqsAsyncClient): Source[Message, NotUsed] =
    RestartSource.withBackoff(RestartSettings(500.millis, 1.second, 0.1)) { () =>
      SqsSource(
        config.sqsQueue,
        SqsSourceSettings.Defaults.withMessageAttribute(MessageAttributeName(KinesisKey))
      )
    }

  case class ParsedMsg(original: Message, key: String, bytes: Array[Byte])

  /** A flow that base64-decodes sqs messages. It ignores messages if they cannot be parsed */
  val sqsMsg2kinesisMsg: Flow[Message, Either[ParsedMsg, ParsedMsg], NotUsed] =
    Flow[Message].map { msg =>
      logger.debug(s"Received message ${msg.messageId}")
      Either.catchNonFatal(java.util.Base64.getDecoder.decode(msg.body)) match {
        case Right(decoded) =>
          val maybeKey = Option(msg.messageAttributes().get(KinesisKey)).map(_.stringValue())
          val key = maybeKey.getOrElse {
            val randomKey = UUID.randomUUID().toString
            logger.warn(s"Kinesis key for sqs message ${msg.messageId()} not found, random key generated: $randomKey")
            randomKey
          }
          Right(ParsedMsg(msg, key, decoded))
        case Left(e) =>
          logger.error("Error decoding sqs message. Message will be sent to bad row stream.", e)
          val failure = Failure.GenericFailure(Instant.now(), NonEmptyList.one("Invalid base64 encoded SQS message"))
          val payload = Payload.RawPayload(msg.body)
          val badRow  = BadRow.GenericError(processor, failure, payload)
          Left(ParsedMsg(msg, Random.nextInt.toString, badRow.compact.getBytes(UTF_8)))
      }
    }

  def toPutRecordReqEntry(msg: ParsedMsg): PutRecordsRequestEntry =
    PutRecordsRequestEntry.builder.partitionKey(msg.key).data(SdkBytes.fromByteArrayUnsafe(msg.bytes)).build

  /** A Flow that tries to send a batch of messages to kinesis. Any failures in the batch will be retried up to 5 times. */
  def kinesisFlow(streamName: String, kinesisClient: KinesisAsyncClient)(
    implicit ec: ExecutionContext
  ): Flow[Vector[ParsedMsg], Message, NotUsed] = {

    // The inner flow, which must be retried on error.
    val inner = Flow[(Vector[ParsedMsg], Vector[ParsedMsg])].mapAsync(1) {
      case (todo, complete) =>
        val req =
          PutRecordsRequest.builder.streamName(streamName).records(todo.map(toPutRecordReqEntry).asJavaCollection).build
        kinesisClient
          .putRecords(req)
          .asScala
          .map { resp =>
            val results               = resp.records.asScala.toList
            val (successes, failures) = todo.zip(results).partition(_._2.errorMessage == null)
            (complete ++ successes.map(_._1), failures.map(_._1))
          }
          .recover {
            case NonFatal(e) =>
              logger.error("Writing to kinesis failed with error", e)
              (complete, todo)
          }
    }

    Flow[Vector[ParsedMsg]]
      .map(_ -> Vector.empty)
      .via(RetryFlow.withBackoff(500.milli, 1.second, 0.0, 5, inner) {
        case (_, (_, Vector())) =>
          None
        case (_, (successes, failures)) =>
          logger.error(
            s"Got ${failures.size} failures and ${successes.size} successes writing to stream ${config.streamName}. Failures will be retried."
          )
          Some((failures, successes))
      })
      .mapConcat {
        case (successes, failures) =>
          if (failures.nonEmpty)
            logger.error(
              s"Got ${failures.size} failures and ${successes.size} successes writing to stream ${config.streamName}. Giving up on failures because max retries exceeded. Failures will not be acked to sqs."
            )
          logger.debug(s"Successfully wrote ${successes.size} messages to kinesis stream ${config.streamName}")
          successes.map(_.original)
      }
  }

  /** A Flow that tries to ack a sqs message. Upon failure, the ack will be retried up to 5 times */
  def confirmSqsSink(config: Sqs2KinesisConfig)(implicit client: SqsAsyncClient): Sink[Message, NotUsed] = {
    val inner = Flow[Message]
      .map(MessageAction.Delete(_))
      .via(SqsAckFlow(config.sqsQueue))
      .map(_ => Option.empty[Throwable])
      .recover {
        case NonFatal(e) => Some(e)
      }

    RetryFlow
      .withBackoff(500.milli, 1.second, 0.0, 5, inner) {
        case (in, Some(e)) =>
          logger.warn("Error acking sqs message. It will be retried", e)
          Some(in)
        case (_, None) => None
      }
      .to(Sink.foreach {
        case Some(e) => logger.error("Exceeded retry limit acking sqs", e)
        case None    => logger.debug("Successfully acked message to sqs")
      })
  }

  def printProgress[T]: Sink[T, NotUsed] =
    Flow[T]
      .groupedWithin(10000, 1.minute)
      .map(msg => s"sqs2kinesis processed ${msg.length} messages")
      .to(Sink.foreach(logger.info(_)))

  // 5 MB - the maximum combined size of a PutRecordsRequest
  val MaxKinesisBytesPerRequest = 5000000

  // The maximum number of records in a PutRecordsRequest
  val MaxKinesisBatch = 500
}
