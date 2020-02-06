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

package com.snowplowanalytics.sqs2kinesis

import org.specs2.mutable.Specification
import java.nio.ByteBuffer

class EventsStreamModuleTest extends Specification {

  def encode(msg: String, key: String, separator: Char): String = {
    val msgBuff = ByteBuffer.wrap(msg.getBytes())
    val msgWithKey =
      ByteBuffer.wrap(Array.concat(key.getBytes, Array(separator.toByte), msgBuff.array))
    val encoded = java.util.Base64.getEncoder.encode(msgWithKey)
    new String(encoded.array())
  }

  def test(
    key: String,
    msg: String,
    separator: Char
  ): (EventsStreamModule.KinesisKeyAndMsg, EventsStreamModule.KinesisKeyAndMsg) = {
    val encoded  = encode(msg, key, separator)
    val expected = (key, ByteBuffer.wrap(msg.getBytes()))
    val result   = EventsStreamModule.sqsMsg2kinesisMsg(separator)(encoded)
    (expected, result)
  }

  "sqsMsg2kinesisMsg" should {
    "convert correct messages" in {
      val (expected, result) = test("key01", "test", '|')
      expected ==== result
    }
    "work with empty key" in {
      val (expected, result) = test("", "test", '|')
      expected ==== result
    }
    "work with empty msg" in {
      val (expected, result) = test("key01", "", '|')
      expected ==== result
    }
    "work with not default separator" in {
      val (expected, result) = test("key01", "message001", ',')
      expected ==== result
    }
    "work with separator in message" in {
      val (expected, result) = test("key01", "message|001", '|')
      expected ==== result
    }
    "work with utf8 characters" in {
      val (expected, result) = test("\u0001\u0002\u0003", "😀👍🤦‍♂️", '|')
      expected ==== result
    }
    "fail if key contains separator" in {
      val (expected, result) = test("key|01", "message001", '|')
      expected != result
    }
  }

}
