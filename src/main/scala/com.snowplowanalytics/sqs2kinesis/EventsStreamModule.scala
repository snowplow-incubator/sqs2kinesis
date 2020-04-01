package com.snowplowanalytics.sqs2kinesis

import com.github.matsluni.akkahttpspi.AkkaHttpClient
import software.amazon.awssdk.services.sqs.SqsAsyncClient
import software.amazon.awssdk.services.sqs.model.Message
import com.amazonaws.services.kinesis.AmazonKinesisAsyncClientBuilder
import com.amazonaws.services.kinesis.model.PutRecordsResultEntry
import com.amazonaws.services.kinesis.model.PutRecordsRequestEntry
import java.nio.ByteBuffer
import akka.actor.ActorSystem
import akka.stream.scaladsl._
import akka.stream.alpakka.sqs.scaladsl.SqsSource
import akka.stream.alpakka.sqs.SqsSourceSettings
import akka.stream.alpakka.kinesis.KinesisFlowSettings
import akka.stream.alpakka.kinesis.scaladsl.KinesisFlow
import akka.stream.alpakka.sqs.MessageAction
import akka.stream.alpakka.sqs.scaladsl.SqsAckSink
import akka.NotUsed
import akka.stream.ActorAttributes
import akka.stream.Supervision
import scala.concurrent.duration._
import com.typesafe.scalalogging.Logger

object EventsStreamModule {

  case class StreamConfig(
    sqsEndpoint: String,
    sqsQueue: String,
    sqsKeyValueSeparator: String,
    kinesisEndpoint: String,
    kinesisStreamName: String
  )

  val logger = Logger[EventsStreamModule.type]

  def runStream(config: StreamConfig)(implicit system: ActorSystem) = {

    implicit val sqsClient = {
      val client = SqsAsyncClient
        .builder()
        .httpClient(AkkaHttpClient.builder().withActorSystem(system).build())
        .build()

      system.registerOnTermination(client.close())
      client
    }

    implicit val kinesisClient = {
      val client = AmazonKinesisAsyncClientBuilder.standard().build()

      system.registerOnTermination(client.shutdown())
      client
    }

    val sqsSource: Source[Message, NotUsed] =
      SqsSource(config.sqsQueue, SqsSourceSettings.Defaults)

    type KinesisKeyAndMsg = (String, ByteBuffer)

    val toPutRecordReqEntry: ((String, ByteBuffer)) => PutRecordsRequestEntry = {
      case (key, data) =>
        new PutRecordsRequestEntry()
          .withPartitionKey(key)
          .withData(data)
    }

    val kinesisFlow
      : Flow[(PutRecordsRequestEntry, Message), (PutRecordsResultEntry, Message), NotUsed] =
      RestartFlow.withBackoff(500.milli, 1.second, 0.2) { () =>
        KinesisFlow
          .withUserContext(
            config.kinesisStreamName,
            KinesisFlowSettings.Defaults.withMaxBatchSize(500).withMaxRetries(10)
          )
      }

    val sqsMsg2kinesisMsg: Message => KinesisKeyAndMsg =
      m => {
        val decoded    = java.util.Base64.getDecoder().decode(m.body)
        val (key, msg) = decoded.splitAt(decoded.indexOf(config.sqsKeyValueSeparator.toByte))
        (new String(key), ByteBuffer.wrap(msg))
      }

    def confirmSqsSink: Sink[Message, NotUsed] =
      Flow[Message]
        .map(MessageAction.Delete(_))
        .to(SqsAckSink(config.sqsQueue))

    val decider: Supervision.Decider = {
      case e @ _ => {
        logger.error("Error in stream:", e)
        Supervision.Resume
      }
    }

    sqsSource
      .asSourceWithContext(m => m)
      .map(sqsMsg2kinesisMsg)
      .map(toPutRecordReqEntry)
      .via(kinesisFlow)
      .asSource
      .map(_._2)
      .alsoTo(confirmSqsSink)
      .toMat(Sink.ignore)(Keep.right)
      .withAttributes(ActorAttributes.supervisionStrategy(decider))
      .run()
  }
}
