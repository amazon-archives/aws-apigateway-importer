/*
 * Copyright 2010-2015 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *  http://aws.amazon.com/apache2.0
 *
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */
package com.amazonaws.service.apigateway.importer.impl;

import com.amazonaws.service.apigateway.importer.SwaggerApiFileImporter;
import com.amazonaws.service.apigateway.importer.SwaggerApiImporter;
import com.google.inject.Inject;
import io.swagger.models.Swagger;
import io.swagger.parser.SwaggerParser;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.File;

import static java.lang.String.format;

public class ApiGatewaySwaggerFileImporter implements SwaggerApiFileImporter {
    private static final Log LOG = LogFactory.getLog(ApiGatewaySwaggerFileImporter.class);

    private final SwaggerParser parser;
    private final SwaggerApiImporter client;

    @Inject
    public ApiGatewaySwaggerFileImporter(SwaggerParser parser, SwaggerApiImporter client) {
        this.parser = parser;
        this.client = client;
    }

    @Override
    public String importApi(String filePath) {
        LOG.info(format("Attempting to create API from Swagger definition. " +
                                "Swagger file: %s", filePath));

        final Swagger swagger = parse(filePath);

        return client.createApi(swagger, new File(filePath).getName());
    }

    @Override
    public void updateApi(String apiId, String filePath) {
        LOG.info(format("Attempting to update API from Swagger definition. " +
                                "API identifier: %s Swagger file: %s", apiId, filePath));

        final Swagger swagger = parse(filePath);

        client.updateApi(apiId, swagger);
    }

    @Override
    public void deploy(String apiId, String deploymentStage) {
        client.deploy(apiId, deploymentStage);
    }

    @Override
    public void deleteApi(String apiId) {
        client.deleteApi(apiId);
    }

    private Swagger parse(String filePath) {
        final Swagger swagger = parser.read(filePath);

        if (swagger != null && swagger.getPaths() != null) {
            LOG.info("Parsed Swagger with " + swagger.getPaths().size() + " paths");
        }

        return swagger;
    }

}
