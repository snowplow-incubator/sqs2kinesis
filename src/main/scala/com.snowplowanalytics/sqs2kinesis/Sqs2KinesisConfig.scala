package com.snowplowanalytics.sqs2kinesis

case class Sqs2KinesisConfig(
  sqsQueue: String,
  kinesisStreamName: String,
  sentryDsn: String
)
