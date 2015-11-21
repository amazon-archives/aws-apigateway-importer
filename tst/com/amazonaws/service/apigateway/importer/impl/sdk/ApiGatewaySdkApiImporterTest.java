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
import org.apache.log4j.BasicConfigurator;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ApiGatewaySdkApiImporterTest {

    private ApiGatewaySdkApiImporter client;

    @Before
    public void setUp() throws Exception {
        BasicConfigurator.configure();

        Injector injector = Guice.createInjector(new SwaggerApiImporterTestModule());
        client = injector.getInstance(ApiGatewaySdkApiImporter.class);
    }

    @Test
    public void testBuildResourcePath_happy() {
        String basePath = "/v1";
        String path = "/1/2/3";
        assertEquals("/v1/1/2/3", client.buildResourcePath(basePath, path));
    }

    @Test
    public void testBuildResourcePath_baseBlank() {
        String basePath = "";
        String path = "/1/2/3";
        assertEquals("/1/2/3", client.buildResourcePath(basePath, path));
    }

    @Test
    public void testBuildResourcePath_baseSlash() {
        String basePath = "/";
        String path = "/1/2/3";
        assertEquals("/1/2/3", client.buildResourcePath(basePath, path));
    }

    @Test
    public void testBuildResourcePath_bothBlank() {
        String basePath = "";
        String path = "";
        assertEquals("/", client.buildResourcePath(basePath, path));
    }

    @Test
    public void testBuildResourcePath_baseTrailingSlash() {
        String basePath = "/v1/";
        String path = "/1/2";
        assertEquals("/v1/1/2", client.buildResourcePath(basePath, path));
    }

    @Test
    public void testBuildResourcePath_bothTrailingSlash() {
        String basePath = "/v1/";
        String path = "/1/2/";
        assertEquals("/v1/1/2", client.buildResourcePath(basePath, path));
    }

    @Test
    public void testBuildResourcePath_bothMissingSlash() {
        String basePath = "v1";
        String path = "1/2";
        assertEquals("/v1/1/2", client.buildResourcePath(basePath, path));
    }

    @Test
    public void testBuildResourcePath_pathTrailingSlash() {
        String basePath = "";
        String path = "/1/2/";
        assertEquals("/1/2", client.buildResourcePath(basePath, path));
    }

    @Test
    public void testBuildResourcePath_pathRoot() {
        String basePath = "/v1";
        String path = "/";
        assertEquals("/v1", client.buildResourcePath(basePath, path));
    }

    @Test
    public void testBuildResourcePath_nested() {
        String basePath = "/v1/2/3";
        String path = "/4/5/6";
        assertEquals("/v1/2/3/4/5/6", client.buildResourcePath(basePath, path));
    }

}
