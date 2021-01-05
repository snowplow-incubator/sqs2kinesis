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

import com.typesafe.sbt.packager.Keys._
import com.typesafe.sbt.packager.docker.DockerPlugin.autoImport.{Docker, dockerExposedPorts, dockerUpdateLatest}
import sbt.Keys._
import sbt._

object BuildSettings {

  lazy val projectSettings = Seq(
    name := "sqs2kinesis",
    version := "0.2.0-rc1",
    organization := "com.snowplowanalytics",
    scalaVersion := "2.13.1"
  )

  lazy val dockerSettings =
    Seq(
      dockerUsername := Some("snowplow"),
      packageName in Docker := "sqs2kinesis",
      dockerExposedPorts ++= Seq(8080),
      dockerUpdateLatest := true,
      dockerBaseImage := "snowplow/base-debian:0.2.2",
      maintainer in Docker := "Snowplow Analytics Ltd. <support@snowplowanalytics.com>",
      daemonUser in Docker := "snowplow",
      daemonUserUid in Docker := None,
      defaultLinuxInstallLocation in Docker := "/home/snowplow"
    )

  lazy val resolverSettings =
    resolvers ++= Seq(
      "Sonatype OSS Snapshots".at("https://oss.sonatype.org/content/repositories/snapshots/")
    )

  lazy val compilerSettings = Seq[Setting[_]](
    scalacOptions := Seq(
      "-deprecation",
      "-encoding",
      "UTF-8",
      "-feature",
      "-unchecked",
      "-Ywarn-dead-code",
      "-Ywarn-numeric-widen",
      "-Ywarn-unused",
      "-Ywarn-value-discard"
    ),
    javacOptions := Seq(
      "-source",
      "11",
      "-target",
      "11",
      "-Xlint"
    )
  )
}
