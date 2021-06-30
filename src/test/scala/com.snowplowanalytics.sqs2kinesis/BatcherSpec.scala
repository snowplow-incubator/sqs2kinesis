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

import akka.stream.scaladsl.{Sink, Source}
import scala.concurrent.Await
import scala.concurrent.duration.DurationLong

import akka.actor.ActorSystem

import org.specs2.mutable.Specification

class BatcherSpec extends Specification {

  implicit val sys = ActorSystem("BatcherSpec")

  "batch" should {
    "create batches that don't exceed a maximum number" in {

      val maxSize   = 3
      val maxWeight = 1000

      val flow = Batcher.batch[String](10.millis, maxSize, maxWeight, _.size)

      val future = Source(List("aaa", "bbb", "ccc", "ddd", "eee")).via(flow).runWith(Sink.seq)

      val result = Await.result(future, 1.second)

      result should contain(exactly(Vector("aaa", "bbb", "ccc"), Vector("ddd", "eee")))
    }

    "create batches that don't exceed a maximum total length" in {

      val maxSize   = 1000
      val maxWeight = 7

      val flow = Batcher.batch[String](10.millis, maxSize, maxWeight, _.size)

      val future = Source(List("aaa", "bbb", "ccc", "ddd", "eee")).via(flow).runWith(Sink.seq)

      val result = Await.result(future, 1.second)

      result should contain(exactly(Vector("aaa", "bbb"), Vector("ccc", "ddd"), Vector("eee")))
    }

    "emit a batch early if the keepAlive time is exceeded" in {

      val maxSize   = 10000
      val maxWeight = 10000

      val keepAlive = 10.millis

      val source = Source(List("aaa", "bbb")).concat(Source.never)

      val flow = Batcher.batch[String](keepAlive, maxSize, maxWeight, _.size)

      val future = source.via(flow).takeWithin(keepAlive * 10).runWith(Sink.seq)

      val result = Await.result(future, keepAlive * 100)

      result should contain(exactly(Vector("aaa", "bbb")))
    }

    "emit no batches if the keepAlive time is never exceeded" in {

      val maxSize   = 10000
      val maxWeight = 10000

      val keepAlive = 10.seconds

      val source = Source(List("aaa", "bbb")).concat(Source.never)

      val flow = Batcher.batch[String](keepAlive, maxSize, maxWeight, _.size)

      val future = source.via(flow).takeWithin(100.millis).runWith(Sink.seq)

      val result = Await.result(future, 1.second)

      result should beEmpty
    }
  }
}
