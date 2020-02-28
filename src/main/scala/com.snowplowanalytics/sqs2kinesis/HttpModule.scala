package com.snowplowanalytics.sqs2kinesis

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._

object HttpModule {

  def runHttpServer(host: String, port: Int)(implicit system: ActorSystem) = {

    implicit val executionContext = system.dispatcher

    val route =
      path("health") {
        get {
          complete(HttpResponse(StatusCodes.OK))
        }
      }

    Http().bindAndHandle(route, host, port)
    println(s"Server online at http://$host:$port")
  }

}
