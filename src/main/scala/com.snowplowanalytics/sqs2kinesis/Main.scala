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

import akka.actor.ActorSystem
import com.typesafe.scalalogging.LazyLogging
import com.typesafe.config.ConfigFactory
import scala.util.Try

object Main extends App with LazyLogging {

  val config = {

    // lack of one of those settings should throw an exception and stop the application
    val conf              = ConfigFactory.load().getConfig("sqs2kinesis")
    val sqsEndpoint       = conf.getString("sqs-endpoint")
    val sqsQueue          = conf.getString("sqs-queue")
    val kinesisEndpoint   = conf.getString("kinesis-endpoint")
    val kinesisStreamName = conf.getString("kinesis-stream-name")
    // this config param has a default value
    val sqsKeyValueSeparator =
      Try(conf.getString("sqs-key-value-separator")).toOption.getOrElse("|")

    val streamConfig = EventsStreamModule.StreamConfig(
      sqsEndpoint,
      sqsQueue,
      sqsKeyValueSeparator,
      kinesisEndpoint,
      kinesisStreamName
    )
    logger.info(s"config: $streamConfig")
    streamConfig
  }

  implicit val system: ActorSystem = ActorSystem()

  EventsStreamModule.runStream(config)
  HttpModule.runHttpServer("0.0.0.0", 8080)

}
