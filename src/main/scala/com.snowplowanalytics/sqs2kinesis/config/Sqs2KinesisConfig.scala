/*
 * Copyright (c) 2020-2022 Snowplow Analytics Ltd. All rights reserved.
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

package com.snowplowanalytics.sqs2kinesis.config

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto._
import io.circe.config.syntax._
import scala.concurrent.duration.FiniteDuration

/**
  * Parsed HOCON configuration file
  *
  * @param input Configures the input sqs queue
  * @param output Configures the output good and bad kinesis streams
  * @param monitoring Configures monitoring. Currently just sentry.
  */
case class Sqs2KinesisConfig(
  input: Sqs2KinesisConfig.SqsConfig,
  output: Sqs2KinesisConfig.Output,
  monitoring: Sqs2KinesisConfig.Monitoring
)

object Sqs2KinesisConfig {

  /** Configure the input sqs queue
    *
    *  @param queue The queue url
    *  @param kinesisKey The sqs metadata key that tells us the output key for kinesis messages
    *  @param minBackoff Minimum backoff before retrying after failure
    *  @param maxBackoff Maximum backoff before retrying after failure
    *  @param randomFactor Random factor when calculating backoff time after failure
    *  @param maxRetries Maximum number of retries after failure to fetch or ack (delete) a message
    *  @param maxRetriesWithin Duration for which the maxRetries are counted, before exiting with failure
    */
  case class SqsConfig(
    queue: String,
    kinesisKey: String,
    minBackoff: FiniteDuration,
    maxBackoff: FiniteDuration,
    randomFactor: Double,
    maxRetries: Int,
    maxRetriesWithin: FiniteDuration
  )

  /** Configure the output kinesis stream
    *
    *  @param streamName The stream name
    *  @param maxKinesisBytesPerRequest The maximum combined size of a PutRecordsRequest
    *  @param maxKinesisBatch The maximum number of records in a PutRecordsRequest
    *  @param keepAlive The maximum time to wait before emitting an incomplete PutRecordsRequest
    *  @param minBackoff Minimum backoff before retrying after failure
    *  @param maxBackoff Maximum backoff before retrying after failure
    *  @param randomFactor Random factor when calculating backoff time after failure
    *  @param maxRetries Maximum number of retries after failure
    */
  case class KinesisConfig(
    streamName: String,
    maxKinesisBytesPerRequest: Int,
    maxKinesisBatch: Int,
    keepAlive: FiniteDuration,
    minBackoff: FiniteDuration,
    maxBackoff: FiniteDuration,
    randomFactor: Double,
    maxRetries: Int
  )

  case class Output(good: KinesisConfig, bad: KinesisConfig)
  case class Sentry(dsn: String)
  case class Health(host: String, port: Int)
  case class Monitoring(sentry: Option[Sentry], health: Health)

  implicit val finiteDurationEncoder: Encoder[FiniteDuration] =
    implicitly[Encoder[String]].contramap(_.toString)

  implicit val sqsDecoder: Decoder[SqsConfig] = deriveDecoder[SqsConfig]
  implicit val sqsEncoder: Encoder[SqsConfig] = deriveEncoder[SqsConfig]

  implicit val kinesisDecoder: Decoder[KinesisConfig] = deriveDecoder[KinesisConfig]
  implicit val kinesisEncoder: Encoder[KinesisConfig] = deriveEncoder[KinesisConfig]

  implicit val outputDecoder: Decoder[Output] = deriveDecoder[Output]
  implicit val outputEncoder: Encoder[Output] = deriveEncoder[Output]

  implicit val healthDecoder: Decoder[Health] = deriveDecoder[Health]
  implicit val healthEncoder: Encoder[Health] = deriveEncoder[Health]

  implicit val sentryDecoder: Decoder[Sentry] = deriveDecoder[Sentry]
  implicit val sentryEncoder: Encoder[Sentry] = deriveEncoder[Sentry]

  implicit val monitoringDecoder: Decoder[Monitoring] = deriveDecoder[Monitoring]
  implicit val monitoringEncoder: Encoder[Monitoring] = deriveEncoder[Monitoring]

  implicit val decoder: Decoder[Sqs2KinesisConfig] = deriveDecoder[Sqs2KinesisConfig]
  implicit val encoder: Encoder[Sqs2KinesisConfig] = deriveEncoder[Sqs2KinesisConfig]

}
