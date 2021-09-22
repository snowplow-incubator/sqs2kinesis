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
import sbtassembly.AssemblyPlugin.autoImport._
import sbtdynver.DynVerPlugin.autoImport._
import sbtbuildinfo.BuildInfoPlugin.autoImport._
import sbt.Keys._
import sbt._

object BuildSettings {

  lazy val projectSettings = Seq(
    name := "sqs2kinesis",
    organization := "com.snowplowanalytics",
    scalaVersion := "2.13.1"
  )

  lazy val dockerSettings =
    Seq(
      packageName in Docker := "sqs2kinesis",
      maintainer in Docker := "Snowplow Analytics Ltd. <support@snowplowanalytics.com>",
      dockerBaseImage := "adoptopenjdk:11-jre-hotspot-focal",
      daemonUser in Docker := "daemon",
      dockerUpdateLatest := true,
      dockerUsername := Some("snowplow"),
      daemonUserUid in Docker := None,
      defaultLinuxInstallLocation in Docker := "/opt/snowplow",
      dockerExposedPorts ++= Seq(8080),
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

  lazy val assemblySettings = Seq(
    assemblyJarName in assembly := { s"${name.value}-${version.value}.jar" },
    assemblyMergeStrategy in assembly := {
      case x if x.endsWith("module-info.class") => MergeStrategy.first
      case x if x.endsWith("io.netty.versions.properties") => MergeStrategy.discard
      case x if x.startsWith("codegen-resources/") => MergeStrategy.discard
      case x if x.endsWith("execution.interceptors") => MergeStrategy.discard
      case x =>
        val oldStrategy = (assembly / assemblyMergeStrategy).value
        oldStrategy(x)
    }
  )

  lazy val dynVerSettings = Seq(
    ThisBuild / dynverVTagPrefix := false, // Otherwise git tags required to have v-prefix
    ThisBuild / dynverSeparator := "-" // to be compatible with docker
    )

  lazy val buildInfoSettings = Seq(
    buildInfoKeys := Seq[BuildInfoKey](name, version),
    buildInfoPackage := "com.snowplowanalytics.sqs2kinesis.generated"
  )

  lazy val addExampleConfToTestCp = Seq(
    Test / unmanagedClasspath += {
      baseDirectory.value / "config"
    }
  )
}
