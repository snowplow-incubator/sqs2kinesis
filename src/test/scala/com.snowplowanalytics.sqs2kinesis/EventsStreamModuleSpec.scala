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
package com.snowplowanalytics.sqs2kinesis

import akka.stream.scaladsl.{Sink, Source}
import akka.actor.ActorSystem
import java.nio.charset.StandardCharsets
import software.amazon.awssdk.services.sqs.model.{Message, MessageAttributeValue}
import scala.concurrent.Await
import scala.concurrent.duration.DurationLong
import scala.jdk.CollectionConverters._

import com.snowplowanalytics.sqs2kinesis.config.Sqs2KinesisConfig

import org.specs2.mutable.Specification

class EventsStreamModuleSpec extends Specification {
  import EventsStreamModuleSpec._

  implicit val sys = ActorSystem("BatcherSpec")

  "sqsMsg2kinesisMsg" should {
    "emit a Right ParsedMsg for a valid sqs message" in {
      val flow = EventsStreamModule.sqsMsg2kinesisMsg(config)

      val message = buildMessage("c25vd3Bsb3c=")

      val future = Source(List(message)).via(flow).runWith(Sink.seq)
      val result = Await.result(future, 1.second)

      result should beLike {
        case Seq(Right(parsed)) =>
          parsed.key must_== kinesisKey
          parsed.bytes.map(_.toChar).mkString must_== "snowplow"
      }

    }

    "emit a Left ParsedMsg for a valid sqs message" in {
      val flow = EventsStreamModule.sqsMsg2kinesisMsg(config)

      val invalidBase64 = "!£$%^&*()"
      val message       = buildMessage(invalidBase64)

      val future = Source(List(message)).via(flow).runWith(Sink.seq)
      val result = Await.result(future, 1.second)

      result should beLike {
        case Seq(Left(parsed)) =>
          parsed.key must_== kinesisKey
          val errorBody = new String(parsed.bytes, StandardCharsets.UTF_8)
          errorBody must contain("Invalid base64 encoded SQS message")
          errorBody must contain(invalidBase64)
      }

    }

  }

}

object EventsStreamModuleSpec {
  val kinesisKey: String = "678c11c2-e80f-4155-bd0e-dec83d571dd1"
  val messageId: String  = "b73f2c49-874e-47c4-9a76-09751e3dc3e2"

  val config: Sqs2KinesisConfig.SqsConfig =
    Sqs2KinesisConfig.SqsConfig("myqueue", "kinesisKey", 1.second, 1.second, 1.0, 1, 1.minute, None)

  def buildMessage(body: String): Message = {
    val keyAttribute = MessageAttributeValue.builder.dataType("String").stringValue(kinesisKey).build

    Message.builder.body(body).messageAttributes(Map("kinesisKey" -> keyAttribute).asJava).build
  }
}
