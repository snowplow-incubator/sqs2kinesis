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
import sbt._

object Dependencies {

  object V {
    val awsSdk       = "2.19.26"
    val sts          = "2.19.26"
    val alpakka      = "3.0.4"
    val akka         = "2.6.18"
    val akkaHttp     = "10.2.7"
    val scalaLogging = "3.9.4"
    val logback      = "1.2.3"
    val specs2       = "4.7.0"
    val sentry       = "1.7.30"
    val badRows      = "2.1.1"
    val decline      = "1.4.0"
    val circeConfig  = "0.8.0"
    val jackson      = "2.13.4.2" // Override default to mitigate CVE
  }

  val awsKinesisSdk  = "software.amazon.awssdk"           % "kinesis"                      % V.awsSdk
  val awsSts         = "software.amazon.awssdk"           % "sts"                          % V.sts % Runtime
  val akkaStream     = "com.typesafe.akka"                %% "akka-stream"                 % V.akka
  val akkaHttp       = "com.typesafe.akka"                %% "akka-http"                   % V.akkaHttp
  val alpakkaSqs     = "com.lightbend.akka"               %% "akka-stream-alpakka-sqs"     % V.alpakka
  val scalaLogging   = "com.typesafe.scala-logging"       %% "scala-logging"               % V.scalaLogging
  val badRows        = "com.snowplowanalytics"            %% "snowplow-badrows"            % V.badRows
  val decline        = "com.monovore"                     %% "decline"                     % V.decline
  val circeConfig    = "io.circe"                         %% "circe-config"                % V.circeConfig
  val sentry         = "io.sentry"                        % "sentry-logback"               % V.sentry
  val logback        = "ch.qos.logback"                   % "logback-classic"              % V.logback % Runtime
  val jackson        = "com.fasterxml.jackson.core"       % "jackson-databind"             % V.jackson
  val specs2         = "org.specs2"                       %% "specs2-core"                 % V.specs2 % Test
}
