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

lazy val root = project
  .in(file("."))
  .enablePlugins(JavaAppPackaging, DockerPlugin, BuildInfoPlugin)
  .settings(BuildSettings.projectSettings)
  .settings(BuildSettings.compilerSettings)
  .settings(BuildSettings.dockerSettings)
  .settings(BuildSettings.assemblySettings)
  .settings(BuildSettings.dynVerSettings)
  .settings(BuildSettings.buildInfoSettings)
  .settings(BuildSettings.addExampleConfToTestCp)
  .settings(
    libraryDependencies ++= Seq(
      Dependencies.awsKinesisSdk,
      Dependencies.akkaStream,
      Dependencies.alpakkaSqs,
      Dependencies.scalaLogging,
      Dependencies.sentry,
      Dependencies.decline,
      Dependencies.circeConfig,
      Dependencies.badRows,
      Dependencies.logback,
      Dependencies.specs2
    )
  )
