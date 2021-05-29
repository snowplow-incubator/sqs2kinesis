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

package com.snowplowanalytics.sqs2kinesis.config

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto._

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
  monitoring: Option[Sqs2KinesisConfig.Monitoring]
)

object Sqs2KinesisConfig {

  case class SqsConfig(queue: String)
  case class KinesisConfig(streamName: String)
  case class Output(good: KinesisConfig, bad: KinesisConfig)
  case class Sentry(dsn: String)
  case class Monitoring(sentry: Option[Sentry])

  implicit val sqsDecoder: Decoder[SqsConfig] = deriveDecoder[SqsConfig]
  implicit val sqsEncoder: Encoder[SqsConfig] = deriveEncoder[SqsConfig]

  implicit val kinesisDecoder: Decoder[KinesisConfig] = deriveDecoder[KinesisConfig]
  implicit val kinesisEncoder: Encoder[KinesisConfig] = deriveEncoder[KinesisConfig]

  implicit val outputDecoder: Decoder[Output] = deriveDecoder[Output]
  implicit val outputEncoder: Encoder[Output] = deriveEncoder[Output]

  implicit val sentryDecoder: Decoder[Sentry] = deriveDecoder[Sentry]
  implicit val sentryEncoder: Encoder[Sentry] = deriveEncoder[Sentry]

  implicit val monitoringDecoder: Decoder[Monitoring] = deriveDecoder[Monitoring]
  implicit val monitoringEncoder: Encoder[Monitoring] = deriveEncoder[Monitoring]

  implicit val decoder: Decoder[Sqs2KinesisConfig] = deriveDecoder[Sqs2KinesisConfig]
  implicit val encoder: Encoder[Sqs2KinesisConfig] = deriveEncoder[Sqs2KinesisConfig]

}
