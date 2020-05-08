/*
 * Copyright (c) 2020-2020 Snowplow Analytics Ltd. All rights reserved.
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
import Keys._

import com.typesafe.sbt.packager.Keys.packageName
import com.typesafe.sbt.packager.docker.DockerPlugin.autoImport.{dockerExposedPorts, dockerUpdateLatest}
import com.typesafe.sbt.packager.docker.DockerPlugin.autoImport.Docker

object BuildSettings {

  lazy val projectSettings = Seq(
    name := "sqs2kinesis",
    version := "0.1.0-rc10-04",
    organization := "com.snowplowanalytics",
    scalaVersion := "2.13.1"
  )

  lazy val dockerSettings =
    Seq(packageName in Docker := "snowplow/sqs2kinesis", dockerExposedPorts ++= Seq(8080), dockerUpdateLatest := true)

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
      "1.8",
      "-target",
      "1.8",
      "-Xlint"
    )
  )
}
