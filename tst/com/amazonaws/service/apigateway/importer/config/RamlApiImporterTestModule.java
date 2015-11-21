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

import com.amazonaws.service.apigateway.importer.RamlApiFileImporter;
import com.amazonaws.service.apigateway.importer.RamlApiImporter;
import com.amazonaws.service.apigateway.importer.impl.ApiGatewayRamlFileImporter;
import com.amazonaws.service.apigateway.importer.impl.sdk.ApiGatewaySdkRamlApiImporter;
import com.amazonaws.services.apigateway.model.ApiGateway;
import com.google.inject.AbstractModule;
import org.mockito.Mockito;

public class RamlApiImporterTestModule extends AbstractModule {

    @Override
    protected void configure() {
        bind(RamlApiFileImporter.class).to(ApiGatewayRamlFileImporter.class);
        bind(RamlApiImporter.class).to(ApiGatewaySdkRamlApiImporter.class);
        bind(ApiGateway.class).toInstance(Mockito.mock(ApiGateway.class));
    }

}
