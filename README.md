# sqs2kinesis

## Quickstart

You can either edit the [sample configuration file](./config/config.minimal.hocon) and mount it into the docker container:

```bash
$ docker run --mount type=bind,source=./example/config.hocon.sample,destination=/config/config.hocon snowplow/sqs2kinesis:1.0.0 --config /config/config.hocon
```

Or alternatively pass each configuration option directly on the command line:

```bash
$ docker run snowplow/sqs2kinesis:1.0.0 -Doutput.good.streamName=goodstream -Doutput.bad.streamName=badstream -Dinput.sqs=https://sqs.eu-central-1.amazonaws.com/000000000000/test-topic
```

## Copyright and License

Snowplow sqs2kinesis is copyright 2020-2021 Snowplow Analytics Ltd.

Licensed under the **[Apache License, Version 2.0][license]** (the "License");
you may not use this software except in compliance with the License.

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.

[travis]: https://travis-ci.org/snowplow-incubator/sqs2kinesis
[travis-image]: https://travis-ci.org/snowplow-incubator/sqs2kinesis.png?branch=master

[license-image]: http://img.shields.io/badge/license-Apache--2-blue.svg?style=flat
[license]: http://www.apache.org/licenses/LICENSE-2.0

[release-image]: http://img.shields.io/badge/release-0.1.0-rc1-blue.svg?style=flat
[releases]: https://github.com/snowplow/sqs2kinesis/releases
