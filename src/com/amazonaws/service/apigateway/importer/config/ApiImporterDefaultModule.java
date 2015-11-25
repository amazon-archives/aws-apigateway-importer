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
package com.amazonaws.service.apigateway.importer.config;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.service.apigateway.importer.ApiImporterMain;
import com.amazonaws.service.apigateway.importer.RamlApiImporter;
import com.amazonaws.service.apigateway.importer.SwaggerApiImporter;
import com.amazonaws.service.apigateway.importer.impl.sdk.ApiGatewaySdkRamlApiImporter;
import com.amazonaws.service.apigateway.importer.impl.sdk.ApiGatewaySdkSwaggerApiImporter;
import com.amazonaws.services.apigateway.AmazonApiGateway;
import com.amazonaws.services.apigateway.model.ApiGateway;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.name.Named;
import com.google.inject.name.Names;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class ApiImporterDefaultModule extends AbstractModule {
    private static final Log LOG = LogFactory.getLog(ApiImporterMain.class);
    private static final String USER_AGENT = "AmazonApiGatewaySwaggerImporter/1.0";

    private final AWSCredentialsProvider awsCredentialsProvider;

    private String region;

    public ApiImporterDefaultModule(AWSCredentialsProvider awsCredentialsProvider, String region) {
        this.awsCredentialsProvider = awsCredentialsProvider;
        this.region = region;

        LOG.info("Using API Gateway endpoint " + getEndpoint(region));
    }

    @Override
    protected void configure() {
        bind(SwaggerApiImporter.class).to(ApiGatewaySdkSwaggerApiImporter.class);
        bind(RamlApiImporter.class).to(ApiGatewaySdkRamlApiImporter.class);
        bind(String.class).annotatedWith(Names.named("region")).toInstance(region);
    }

    @Provides
    protected AWSCredentialsProvider provideCredentialsProvider() {
        return awsCredentialsProvider;
    }

    @Provides
    protected ApiGateway provideAmazonApiGateway(AWSCredentialsProvider credsProvider,
                                       @Named("region") String region) {
        ClientConfiguration clientConfig = new ClientConfiguration().withUserAgent(USER_AGENT);
        return new AmazonApiGateway(getEndpoint(region)).with(credsProvider).with(clientConfig).getApiGateway();
    }

    protected String getEndpoint(String region) {
        return String.format("https://apigateway.%s.amazonaws.com", region);
    }
}