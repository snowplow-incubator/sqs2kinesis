[![Release][release-image]][releases]
[![License][license-image]][license]
[![Test][test-image]][test]

# sqs2kinesis

An application for reading base64-encoded messages from an sqs queue and writing them to a kinesis stream.

sqs2kinesis can be used as an optional enhancement to a Snowplow pipeline, for when your
[Snowplow collector][snowplow-collector] is configured to use a sqs buffer to handle traffic spikes.

## Find out more

| Technical Docs             | Setup Guide          | Roadmap              | Contributing                 |
|:--------------------------:|:--------------------:|:--------------------:|:----------------------------:|
| ![i1][techdocs-image]      | ![i2][setup-image]   | ![i3][roadmap-image] | ![i4][contributing-image]    |
| [Technical Docs][techdocs] | [Setup Guide][setup] | [Roadmap][roadmap]   | [Contributing][contributing] |

## Copyright and License

Snowplow sqs2kinesis is copyright 2020-2022 Snowplow Analytics Ltd.

Licensed under the **[Apache License, Version 2.0][license]** (the "License");
you may not use this software except in compliance with the License.

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.

[techdocs-image]: https://d3i6fms1cm1j0i.cloudfront.net/github/images/techdocs.png
[setup-image]: https://d3i6fms1cm1j0i.cloudfront.net/github/images/setup.png
[roadmap-image]: https://d3i6fms1cm1j0i.cloudfront.net/github/images/roadmap.png
[contributing-image]: https://d3i6fms1cm1j0i.cloudfront.net/github/images/contributing.png

[techdocs]: https://docs.snowplow.io/docs/pipeline-components-and-applications/sqs2kinesis/
[setup]: https://docs.snowplow.io/docs/pipeline-components-and-applications/sqs2kinesis/sqs2kinesis-configuration-reference/
[roadmap]: https://github.com/snowplow/snowplow/projects/7
[contributing]: https://docs.snowplow.io/docs/contributing/

[test]: https://github.com/snowplow-incubator/sqs2kinesis/actions/workflows/ci.yml
[test-image]: https://github.com/snowplow-incubator/sqs2kinesis/actions/workflows/ci.yml/badge.svg

[license]: http://www.apache.org/licenses/LICENSE-2.0
[license-image]: http://img.shields.io/badge/license-Apache--2-blue.svg?style=flat

[release-image]: http://img.shields.io/badge/release_1.0.5-blue.svg?style=flat
[releases]: https://github.com/snowplow-incubator/sqs2kinesis/releases

[snowplow-collector]: https://github.com/snowplow/stream-collector/
