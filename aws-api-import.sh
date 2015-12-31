#!/bin/bash
root=$(dirname $(perl -MCwd=abs_path -e 'print abs_path(shift)' $0))

java -jar $root/target/aws-apigateway-importer-1.0.3-SNAPSHOT-jar-with-dependencies.jar "$@"