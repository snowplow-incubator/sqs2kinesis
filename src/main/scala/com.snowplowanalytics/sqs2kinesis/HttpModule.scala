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
