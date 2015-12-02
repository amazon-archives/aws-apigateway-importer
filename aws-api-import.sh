#!/bin/bash
root=$(dirname "$(readlink -f "$0")")

java -jar $root/target/aws-apigateway-importer-1.0.3-SNAPSHOT-jar-with-dependencies.jar "$@"