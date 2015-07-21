#!/bin/bash

java -cp build/maven/aws-apigateway-swagger-importer-1.0.0-jar-with-dependencies.jar com.amazonaws.service.apigateway.importer.ApiImporterMain "$@"
