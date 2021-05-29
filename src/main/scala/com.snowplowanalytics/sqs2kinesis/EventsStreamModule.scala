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
import com.snowplowanalytics.sqs2kinesis.config.Sqs2KinesisConfig

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

    val goodSink = sink(config.output.good, config.input, kinesisClient)
    val badSink  = sink(config.output.bad, config.input, kinesisClient)

    sqsSource(config.input)
      .via(sqsMsg2kinesisMsg(config.input))
      .alsoTo(Flow[Either[ParsedMsg, ParsedMsg]].mapConcat(_.toSeq).to(goodSink))
      .alsoTo(Flow[Either[ParsedMsg, ParsedMsg]].mapConcat(_.left.toSeq).to(badSink))
      .to(printProgress)
      .run()
  }

  /** Sink messages to kinesis in batches, and then ack each message to sqs */
  def sink(
    kinesisConfig: Sqs2KinesisConfig.KinesisConfig,
    sqsConfig: Sqs2KinesisConfig.SqsConfig,
    kinesisClient: KinesisAsyncClient
  )(implicit ec: ExecutionContext, sqsClient: SqsAsyncClient): Sink[ParsedMsg, NotUsed] =
    Batcher
      .batch[ParsedMsg](
        keepAlive = kinesisConfig.keepAlive,
        maxSize   = kinesisConfig.maxKinesisBatch,
        maxWeight = kinesisConfig.maxKinesisBytesPerRequest,
        toWeight = { m =>
          m.bytes.size + m.key.getBytes.size
        }
      )
      .via(kinesisFlow(kinesisConfig, kinesisClient))
      .to(confirmSqsSink(sqsConfig))

  /** A source that reads messages from sqs and retries if read was unsuccessful */
  def sqsSource(config: Sqs2KinesisConfig.SqsConfig)(implicit client: SqsAsyncClient): Source[Message, NotUsed] =
    RestartSource.withBackoff(RestartSettings(config.minBackoff, config.maxBackoff, config.randomFactor)) { () =>
      SqsSource(
        config.queue,
        SqsSourceSettings.Defaults.withMessageAttribute(MessageAttributeName(config.kinesisKey))
      )
    }

  case class ParsedMsg(original: Message, key: String, bytes: Array[Byte])

  /** A flow that base64-decodes sqs messages. It ignores messages if they cannot be parsed */
  def sqsMsg2kinesisMsg(config: Sqs2KinesisConfig.SqsConfig): Flow[Message, Either[ParsedMsg, ParsedMsg], NotUsed] =
    Flow[Message].map { msg =>
      logger.debug(s"Received message ${msg.messageId}")
      Either.catchNonFatal(java.util.Base64.getDecoder.decode(msg.body)) match {
        case Right(decoded) =>
          val maybeKey = Option(msg.messageAttributes().get(config.kinesisKey)).map(_.stringValue())
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
  def kinesisFlow(config: Sqs2KinesisConfig.KinesisConfig, kinesisClient: KinesisAsyncClient)(
    implicit ec: ExecutionContext
  ): Flow[Vector[ParsedMsg], Message, NotUsed] = {

    // The inner flow, which must be retried on error.
    val inner = Flow[(Vector[ParsedMsg], Vector[ParsedMsg])].mapAsync(1) {
      case (todo, complete) =>
        val req =
          PutRecordsRequest
            .builder
            .streamName(config.streamName)
            .records(todo.map(toPutRecordReqEntry).asJavaCollection)
            .build
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
      .via(RetryFlow.withBackoff(config.minBackoff, config.maxBackoff, config.randomFactor, config.maxRetries, inner) {
        case (_, (_, Vector())) =>
          None
        case (_, (successes, failures)) =>
          if (failures.nonEmpty)
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
  def confirmSqsSink(config: Sqs2KinesisConfig.SqsConfig)(implicit client: SqsAsyncClient): Sink[Message, NotUsed] = {
    val inner = Flow[Message]
      .map(MessageAction.Delete(_))
      .via(SqsAckFlow(config.queue))
      .map(_ => Option.empty[Throwable])
      .recover {
        case NonFatal(e) => Some(e)
      }

    RetryFlow
      .withBackoff(config.minBackoff, config.maxBackoff, config.randomFactor, config.maxRetries, inner) {
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
}
