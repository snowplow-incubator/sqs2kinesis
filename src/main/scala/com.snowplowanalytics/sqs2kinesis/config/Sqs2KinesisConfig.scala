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
  * @param sqsQueue The input sqs queue
  * @param goodStreamName Output Kinesis stream for base64-decoded sqs messages
  * @param badStreamName Output Kinesis stream for sqs messages that could not be base64-decoded
  * @param sentryDsn Enable sentry monitoring
  */
case class Sqs2KinesisConfig(
  sqsQueue: String,
  goodStreamName: String,
  badStreamName: String,
  sentryDsn: Option[String]
)

object Sqs2KinesisConfig {

  implicit val decoder: Decoder[Sqs2KinesisConfig] = deriveDecoder[Sqs2KinesisConfig]
  implicit val encoder: Encoder[Sqs2KinesisConfig] = deriveEncoder[Sqs2KinesisConfig]

}
