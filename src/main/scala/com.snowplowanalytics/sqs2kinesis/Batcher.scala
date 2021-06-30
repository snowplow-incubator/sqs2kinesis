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

import akka.NotUsed
import akka.stream.scaladsl.{Flow, Source}
import scala.concurrent.duration.FiniteDuration

object Batcher {

  /** Putting messages into batches, not exceeding size constraints
    *
    *  @param keepAlive Forces batches to get emitted early even if batch is not full.
    *  @param maxSize The maximum number of elements allowed in a batch
    *  @param maxWeight The maximum "weight" of an element. For example, this could be it's size in bytes.
    *  @param toWeight How to calculate the weight of an element
    *  @return A Flow that emits batches
    */
  def batch[T](
    keepAlive: FiniteDuration,
    maxSize: Int,
    maxWeight: Int,
    toWeight: T => Int
  ): Flow[T, Vector[T], NotUsed] =
    Flow[T]
      .map(m => Message(m, toWeight(m)))
      .concat(Source(Seq(Flush)))
      .keepAlive(keepAlive, () => Flush)
      .via(scanFlush(State[T](Vector.empty, 0)) {
        case (State(acc, _), Flush) =>
          (State(Vector.empty, 0), Some(acc))
        case (State(acc, accWeight), Message(value, weight)) =>
          val combined = accWeight + weight
          if (combined >= maxWeight)
            (State(Vector(value), weight), Some(acc))
          else if (acc.size + 1 >= maxSize)
            (State(Vector.empty, 0), Some(acc :+ value))
          else
            (State(acc :+ value, combined), None)
      })
      .filter(_.nonEmpty)

  sealed private trait Action[+T]
  private object Flush extends Action[Nothing]
  private case class Message[T](value: T, weight: Int) extends Action[T]

  private case class State[T](acc: Vector[T], weight: Int)

  def scanFlush[S, In, Out](zero: S)(f: (S, In) => (S, Option[Out])): Flow[In, Out, NotUsed] =
    Flow[In]
      .scan((zero, Option.empty[Out])) {
        case ((state, _), next) => f(state, next)
      }
      .collect {
        case (_, Some(out)) => out
      }

}
