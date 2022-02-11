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

import scala.concurrent.duration.DurationLong
import java.nio.file.Paths

import org.specs2.mutable.Specification

import Sqs2KinesisConfig._

class CliConfigSpec extends Specification {
  "parse" should {
    "parse minimal HOCON file with path provided" in {
      val configPath = Paths.get(getClass.getResource("/config.minimal.hocon").toURI)
      val expected = Sqs2KinesisConfig(
        SqsConfig(
          "https://sqs.eu-central-1.amazonaws.com/000000000000/test-topic",
          "kinesisKey",
          500.millis,
          5.second,
          0.1,
          5,
          1.minute
        ),
        Output(
          KinesisConfig("test-stream-payloads", 5000000, 500, 1.second, 500.millis, 1.second, 0.1, 5),
          KinesisConfig("test-stream-bad", 5000000, 500, 1.second, 500.millis, 1.second, 0.1, 5)
        ),
        Monitoring(None, Health("0.0.0.0", 8080))
      )
      CliConfig.parse(Seq("--config", configPath.toString)).map(_.app) must beRight(expected)
    }

    "parse reference HOCON file with path provided" in {
      val configPath = Paths.get(getClass.getResource("/config.reference.hocon").toURI)
      val expected = Sqs2KinesisConfig(
        SqsConfig(
          "https://sqs.eu-central-1.amazonaws.com/000000000000/test-topic",
          "kinesisKey",
          500.millis,
          5.second,
          0.1,
          5,
          1.minute
        ),
        Output(
          KinesisConfig("test-stream-payloads", 5000000, 500, 1.second, 500.millis, 1.second, 0.1, 5),
          KinesisConfig("test-stream-bad", 5000000, 500, 1.second, 500.millis, 1.second, 0.1, 5)
        ),
        Monitoring(Some(Sentry("http://sentry.acme.com")), Health("0.0.0.0", 8080))
      )
      CliConfig.parse(Seq("--config", configPath.toString)).map(_.app) must beRight(expected)
    }
  }

}
