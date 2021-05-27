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
import sbt._

object Dependencies {

  object V {
    val awsSdk       = "2.16.102"
    val alpakka      = "3.0.2"
    val akka         = "2.6.14"
    val scalaLogging = "3.9.2"
    val config       = "1.4.0"
    val logback      = "1.2.3"
    val specs2       = "4.7.0"
    val sentry       = "1.7.30"
    val badRows      = "2.1.0"
  }

  val awsKinesisSdk  = "software.amazon.awssdk"           % "kinesis"                      % V.awsSdk
  val akkaStream     = "com.typesafe.akka"                %% "akka-stream"                 % V.akka
  val alpakkaSqs     = "com.lightbend.akka"               %% "akka-stream-alpakka-sqs"     % V.alpakka
  val scalaLogging   = "com.typesafe.scala-logging"       %% "scala-logging"               % V.scalaLogging
  val badRows        = "com.snowplowanalytics"            %% "snowplow-badrows"            % V.badRows
  val config         = "com.typesafe"                     % "config"                       % V.config
  val sentry         = "io.sentry"                        % "sentry-logback"               % V.sentry
  val logback        = "ch.qos.logback"                   % "logback-classic"              % V.logback % Runtime
  val specs2         = "org.specs2"                       %% "specs2-core"                 % V.specs2 % Test
}
