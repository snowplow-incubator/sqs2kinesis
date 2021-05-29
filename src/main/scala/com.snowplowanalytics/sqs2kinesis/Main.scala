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

import akka.actor.ActorSystem
import com.typesafe.scalalogging.LazyLogging
import io.sentry.Sentry
import io.circe.syntax._

import com.snowplowanalytics.sqs2kinesis.config.CliConfig

object Main extends App with LazyLogging {

  CliConfig.parse(args.toIndexedSeq) match {
    case Right(CliConfig(sqs2KinesisConfig, rawConfig)) =>
      logger.info(sqs2KinesisConfig.asJson.noSpaces)
      sqs2KinesisConfig.monitoring.flatMap(_.sentry).foreach(s => Sentry.init(s.dsn))

      implicit val system: ActorSystem = ActorSystem("sqs2kinesis", rawConfig)

      EventsStreamModule.runStream(sqs2KinesisConfig)
      HttpModule.runHttpServer("0.0.0.0", 8080) // HTTP server for health check

    case Left(error) =>
      println(error)
      sys.exit(1)
  }

}
