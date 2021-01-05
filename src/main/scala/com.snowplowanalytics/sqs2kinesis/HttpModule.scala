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
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import com.typesafe.scalalogging.Logger

object HttpModule {

  val logger = Logger[HttpModule.type]

  def runHttpServer(host: String, port: Int)(implicit system: ActorSystem) = {

    implicit val executionContext = system.dispatcher

    val route =
      path("health") {
        get {
          complete(HttpResponse(StatusCodes.OK))
        }
      }

    Http().bindAndHandle(route, host, port)
    logger.info(s"Server online at http://$host:$port")
  }

}
