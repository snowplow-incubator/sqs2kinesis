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

lazy val root = project
  .in(file("."))
  .enablePlugins(JavaAppPackaging, DockerPlugin)
  .settings(BuildSettings.projectSettings)
  .settings(BuildSettings.compilerSettings)
  .settings(BuildSettings.dockerSettings)
  .settings(
    libraryDependencies ++= Seq(
      Dependencies.awsSqsSdk,
      Dependencies.awsKinesisSdk,
      Dependencies.akkaStream,
      Dependencies.alpakkaSqs,
      Dependencies.scalaLogging,
      Dependencies.config,
      Dependencies.cbor,
      Dependencies.sentry,
      Dependencies.logback,
      Dependencies.specs2
    )
  )
