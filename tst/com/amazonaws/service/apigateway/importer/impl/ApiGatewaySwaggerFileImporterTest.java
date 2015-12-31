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
import com.amazonaws.service.apigateway.importer.config.SwaggerApiImporterTestModule;
import com.amazonaws.services.apigateway.model.ApiGateway;
import com.amazonaws.services.apigateway.model.Integration;
import com.amazonaws.services.apigateway.model.Method;
import com.amazonaws.services.apigateway.model.Model;
import com.amazonaws.services.apigateway.model.Models;
import com.amazonaws.services.apigateway.model.Resource;
import com.amazonaws.services.apigateway.model.Resources;
import com.amazonaws.services.apigateway.model.RestApi;
import com.amazonaws.services.apigateway.model.RestApis;
import com.google.inject.Guice;
import com.google.inject.Injector;
import org.apache.log4j.BasicConfigurator;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;

import java.net.URISyntaxException;
import java.nio.file.Paths;
import java.util.Arrays;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.argThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ApiGatewaySwaggerFileImporterTest {
    private SwaggerApiFileImporter importer;

    private final String API_GATEWAY = "/swagger/apigateway.json";

    @Mock
    private ApiGateway client;

    @Mock
    private Resource mockResource;

    @Mock
    private Resource mockChildResource;

    @Mock
    private RestApi mockRestApi;

    @Before
    public void setUp() throws Exception {
        BasicConfigurator.configure();

        Injector injector = Guice.createInjector(new SwaggerApiImporterTestModule());

        client = injector.getInstance(ApiGateway.class);
        importer = injector.getInstance(SwaggerApiFileImporter.class);

        RestApis mockRestApis = mock(RestApis.class);
        Integration mockIntegration = Mockito.mock(Integration.class);

        Method mockMethod = Mockito.mock(Method.class);
        when(mockMethod.getHttpMethod()).thenReturn("GET");
        when(mockMethod.putIntegration(any())).thenReturn(mockIntegration);

        mockChildResource = Mockito.mock(Resource.class);
        when(mockChildResource.getPath()).thenReturn("/child");
        when(mockChildResource.putMethod(any(), any())).thenReturn(mockMethod);

        mockResource = Mockito.mock(Resource.class);
        when(mockResource.getPath()).thenReturn("/");
        when(mockResource.createResource(any())).thenReturn(mockChildResource);
        when(mockResource.putMethod(any(), any())).thenReturn(mockMethod);

        Resources mockResources = mock(Resources.class);
        when(mockResources.getItem()).thenReturn(Arrays.asList(mockResource));

        Model mockModel = Mockito.mock(Model.class);
        when(mockModel.getName()).thenReturn("test model");

        Models mockModels = mock(Models.class);
        when(mockModels.getItem()).thenReturn(Arrays.asList(mockModel));

        mockRestApi = mock(RestApi.class);
        when(mockRestApi.getResources()).thenReturn(mockResources);
        when(mockRestApi.getModels()).thenReturn(mockModels);
        when(mockRestApi.getResourceById(any())).thenReturn(mockResource);

        when(client.getRestApis()).thenReturn(mockRestApis);
        when(client.createRestApi(any())).thenReturn(mockRestApi);

        importer.importApi(getResourcePath(API_GATEWAY));
    }

    @Test
    public void testImport_create_api() throws Exception {
        verify(client, times(1)).createRestApi(any());
    }

    @Test
    public void testImport_create_resources() throws Exception {
        // to simplify mocking, all child resources are added to the root resource, and parent resources will be added multiple times
        // /v1, /v1/products, /v1, /v1/products, /v1/products/child
        verify(mockResource, atLeastOnce()).createResource(argThat(new LambdaMatcher<>(i -> i.getPathPart().equals("v1"))));
        verify(mockResource, atLeastOnce()).createResource(argThat(new LambdaMatcher<>(i -> i.getPathPart().equals("products"))));
        verify(mockResource, atLeastOnce()).createResource(argThat(new LambdaMatcher<>(i -> i.getPathPart().equals("child"))));
    }

    @Test
    public void testImport_create_methods() throws Exception {
        verify(mockChildResource, times(1)).putMethod(
                argThat(new LambdaMatcher<>(i -> i.getAuthorizationType().equals("AWS_IAM"))),
                argThat(new LambdaMatcher<>(i -> i.equals("GET"))));
        verify(mockChildResource, times(1)).putMethod(
                argThat(new LambdaMatcher<>(i -> i.getAuthorizationType().equals("NONE"))),
                argThat(new LambdaMatcher<>(i -> i.equals("POST"))));
    }

    @Test
    public void testImport_create_models() throws Exception {
        verify(mockRestApi, times(1)).createModel(argThat(new LambdaMatcher<>(i -> i.getName().equals("Product"))));
        verify(mockRestApi, times(1)).createModel(argThat(new LambdaMatcher<>(i -> i.getName().equals("PriceEstimate"))));
        verify(mockRestApi, times(1)).createModel(argThat(new LambdaMatcher<>(i -> i.getName().equals("Profile"))));
        verify(mockRestApi, times(1)).createModel(argThat(new LambdaMatcher<>(i -> i.getName().equals("Activity"))));
        verify(mockRestApi, times(1)).createModel(argThat(new LambdaMatcher<>(i -> i.getName().equals("Activities"))));
        verify(mockRestApi, times(1)).createModel(argThat(new LambdaMatcher<>(i -> i.getName().equals("Error"))));
        verify(mockRestApi, atLeastOnce()).createModel(argThat(new LambdaMatcher<>(i -> i.getName().equals("Anarrayofproducts"))));
    }

    //    todo: add more tests
    private String getResourcePath(String path) throws URISyntaxException {
        return Paths.get(getClass().getResource(path).toURI()).toString();
    }

}
