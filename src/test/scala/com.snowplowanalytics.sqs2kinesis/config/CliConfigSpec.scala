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

import java.nio.file.Paths

import org.specs2.mutable.Specification

import Sqs2KinesisConfig._

class CliConfigSpec extends Specification {
  "parse" should {
    "parse valid HOCON file with path provided" in {
      val configPath = Paths.get(getClass.getResource("/config.hocon.sample").toURI)
      val expected = Sqs2KinesisConfig(
        SqsConfig("https://sqs.eu-central-1.amazonaws.com/000000000000/test-topic"),
        Output(KinesisConfig("test-stream-payloads"), KinesisConfig("test-stream-bad")),
        Some(Monitoring(Some(Sentry("http://sentry.acme.com"))))
      )
      CliConfig.parse(Seq("--config", configPath.toString)).map(_.app) must beRight(expected)
    }
  }

}
