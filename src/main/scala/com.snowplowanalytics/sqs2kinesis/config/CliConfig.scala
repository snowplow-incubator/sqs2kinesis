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

package com.snowplowanalytics.sqs2kinesis.config

import cats.syntax.either._
import cats.syntax.show._
import io.circe.config.parser
import com.monovore.decline.{Command, Opts}
import com.typesafe.config.{Config, ConfigFactory}

import com.snowplowanalytics.sqs2kinesis.generated.BuildInfo

import java.nio.file.Path
import scala.io.Source

case class CliConfig(app: Sqs2KinesisConfig, raw: Config)

object CliConfig {

  val configOpt: Opts[Option[Path]] = Opts.option[Path]("config", "Path to configuration hocon file").orNone

  val command = Command[Option[Path]](BuildInfo.name, BuildInfo.version)(configOpt)

  /**
    * Parse raw CLI arguments into validated and transformed application config
    *
    * @param argv list of command-line arguments, including optionally a `--config` argument
    * @return The parsed config using the provided file and the standard typesafe config loading process.
    *         See https://github.com/lightbend/config/tree/v1.4.1#standard-behavior
    *         Or an error message if config could not be loaded.
    */
  def parse(argv: Seq[String]): Either[String, CliConfig] =
    command.parse(argv).leftMap(_.show).flatMap {
      case Some(path) =>
        for {
          raw    <- loadFromFile(path)
          parsed <- parser.decode[Sqs2KinesisConfig](raw).leftMap(e => s"Could not parse config $path: ${e.show}")
        } yield CliConfig(parsed, raw)
      case None =>
        val raw = ConfigFactory.load()
        parser
          .decode[Sqs2KinesisConfig](raw)
          .leftMap(e => s"Could not resolve config without a provided hocon file: ${e.show}")
          .map(CliConfig(_, raw))
    }

  /** Uses the typesafe config layering approach. Loads configurations in the following priority order:
    *  1. System properties
    *  2. The provided configuration file
    *  3. application.conf of our app
    *  4. reference.conf of any libraries we use
    */
  def loadFromFile(file: Path): Either[String, Config] =
    for {
      text <- Either
        .catchNonFatal(Source.fromFile(file.toFile).mkString)
        .leftMap(e => s"Could not read config file: ${e.getMessage}")
      resolved <- Either
        .catchNonFatal(ConfigFactory.parseString(text).resolve)
        .leftMap(e => s"Could not parse config file $file: ${e.getMessage}")
    } yield namespaced(ConfigFactory.load(namespaced(resolved.withFallback(namespaced(ConfigFactory.load())))))

  /** Optionally give precedence to configs wrapped in a "snowplow" block. To help avoid polluting config namespace */
  private def namespaced(config: Config): Config =
    if (config.hasPath(Namespace))
      config.getConfig(Namespace).withFallback(config.withoutPath(Namespace))
    else
      config

  private val Namespace = "sqs2kinesis"

}
