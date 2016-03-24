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
package com.amazonaws.service.apigateway.importer.impl.sdk;

import com.amazonaws.service.apigateway.importer.config.SwaggerApiImporterTestModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import io.swagger.models.Response;
import junit.framework.Assert;
import org.apache.log4j.BasicConfigurator;
import org.junit.Before;
import org.junit.Test;

public class ApiGatewaySdkSwaggerApiImporterTest {

    private ApiGatewaySdkSwaggerApiImporter client;

    @Before
    public void setUp() throws Exception {
        BasicConfigurator.configure();

        Injector injector = Guice.createInjector(new SwaggerApiImporterTestModule());
        client = injector.getInstance(ApiGatewaySdkSwaggerApiImporter.class);
    }

    @Test
    public void testGenerateModelName_description() {
        Response r = new Response();
        r.setDescription("Descriptive text will be converted to model name !@$%#^$#%");
        Assert.assertEquals("Wrong model name", "Descriptivetextwillbeconvertedtomodelname", client.generateModelName(r));
    }

    @Test
    public void testGenerateModelName_guid() {
        String generated = client.generateModelName(new Response());
        Assert.assertTrue("Wrong model name", generated.startsWith("model"));
        Assert.assertEquals("Wrong model name", 13, generated.length());
    }

}
