#!/bin/sh
set -ex

echo "$DOCKER_PASSWORD" | docker login -u "$DOCKER_USERNAME" --password-stdin

sbt docker:publish
