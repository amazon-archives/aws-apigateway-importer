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

import com.amazonaws.services.apigateway.model.ApiGateway;
import com.amazonaws.services.apigateway.model.CreateDeploymentInput;
import com.amazonaws.services.apigateway.model.CreateModelInput;
import com.amazonaws.services.apigateway.model.CreateResourceInput;
import com.amazonaws.services.apigateway.model.CreateRestApiInput;
import com.amazonaws.services.apigateway.model.Model;
import com.amazonaws.services.apigateway.model.Models;
import com.amazonaws.services.apigateway.model.NotFoundException;
import com.amazonaws.services.apigateway.model.Resource;
import com.amazonaws.services.apigateway.model.Resources;
import com.amazonaws.services.apigateway.model.RestApi;
import com.google.common.util.concurrent.RateLimiter;
import com.google.inject.Inject;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import static com.amazonaws.service.apigateway.importer.util.PatchUtils.createPatchDocument;
import static com.amazonaws.service.apigateway.importer.util.PatchUtils.createReplaceOperation;

public class ApiGatewaySdkApiImporter {

    private static final Log LOG = LogFactory.getLog(ApiGatewaySdkApiImporter.class);

    @Inject
    protected ApiGateway apiGateway;

    // keep track of the models created/updated from the definition file. Any orphaned models left in the API will be deleted
    protected HashSet<String> processedModels = new HashSet<>();

    public void deleteApi(String apiId) {
        deleteApi(apiGateway.getRestApiById(apiId));
    }

    public void deploy(String apiId, String deploymentStage) {
        LOG.info(String.format("Creating deployment for API %s and stage %s", apiId, deploymentStage));

        CreateDeploymentInput input = new CreateDeploymentInput();
        input.setStageName(deploymentStage);

        apiGateway.getRestApiById(apiId).createDeployment(input);
    }

    protected RestApi createApi(String name, String description) {
        LOG.info("Creating API with name " + name);

        CreateRestApiInput input = new CreateRestApiInput();
        input.setName(name);
        input.setDescription(description);

        return apiGateway.createRestApi(input);
    }

    protected void rollback(RestApi api) {
        deleteApi(api);
    }

    protected void deleteApi(RestApi api) {
        LOG.info("Deleting API " + api.getId());
        api.deleteRestApi();
    }

    protected Optional<Resource> getRootResource(RestApi api) {
        for (Resource r : buildResourceList(api)) {
            if ("/".equals(r.getPath())) {
                return Optional.of(r);
            }
        }
        return Optional.empty();
    }

    // todo: optimize number of calls to this as it is an expensive operation
    protected List<Resource> buildResourceList(RestApi api) {
        List<Resource> resourceList = new ArrayList<>();

        Resources resources = api.getResources();
        resourceList.addAll(resources.getItem());

        LOG.debug("Building list of resources. Stack trace: ", new Throwable());

        final RateLimiter rl = RateLimiter.create(2);
        while (resources._isLinkAvailable("next")) {
            rl.acquire();
            resources = resources.getNext();
            resourceList.addAll(resources.getItem());
        }

        return resourceList;
    }

    protected void deleteDefaultModels(RestApi api) {
        buildModelList(api).stream().forEach(model -> {
            LOG.info("Removing default model " + model.getName());
            try {
                model.deleteModel();
            } catch (Throwable ignored) {
            } // todo: temporary catch until API fix
        });
    }

    protected List<Model> buildModelList(RestApi api) {
        List<Model> modelList = new ArrayList<>();

        Models models = api.getModels();
        modelList.addAll(models.getItem());

        while (models._isLinkAvailable("next")) {
            models = models.getNext();
            modelList.addAll(models.getItem());
        }

        return modelList;
    }

    protected RestApi getApi(String id) {
        return apiGateway.getRestApiById(id);
    }

    protected void createModel(RestApi api, String modelName, String description, String schema, String modelContentType) {
        this.processedModels.add(modelName);

        CreateModelInput input = new CreateModelInput();

        input.setName(modelName);
        input.setDescription(description);
        input.setContentType(modelContentType);
        input.setSchema(schema);

        api.createModel(input);
    }

    protected void updateModel(RestApi api, String modelName, String schema) {
        this.processedModels.add(modelName);

        api.getModelByName(modelName).updateModel(createPatchDocument(createReplaceOperation("/schema", schema)));
    }

    protected void cleanupModels(RestApi api, Set<String> models) {
        List<Model> existingModels = buildModelList(api);
        Stream<Model> modelsToDelete = existingModels.stream().filter(model -> !models.contains(model.getName()));

        modelsToDelete.forEach(model -> {
            LOG.info("Removing deleted model " + model.getName());
            model.deleteModel();
        });
    }

    protected Optional<Resource> getResource(String parentResourceId, String pathPart, List<Resource> resources) {
        for (Resource r : resources) {
            if (pathEquals(pathPart, r.getPathPart()) && r.getParentId().equals(parentResourceId)) {
                return Optional.of(r);
            }
        }
        return Optional.empty();
    }

    protected boolean pathEquals(String p1, String p2) {
        return (StringUtils.isBlank(p1) && StringUtils.isBlank(p2)) || p1.equals(p2);
    }

    protected Optional<Resource> getResource(RestApi api, String fullPath) {
        for (Resource r : buildResourceList(api)) {
            if (r.getPath().equals(fullPath)) {
                return Optional.of(r);
            }
        }

        return Optional.empty();
    }

    protected Optional<Model> getModel(RestApi api, String modelName) {
        try {
            return Optional.of(api.getModelByName(modelName));
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    protected boolean methodExists(Resource resource, String httpMethod) {
        return resource.getResourceMethods().get(httpMethod.toUpperCase()) != null;
    }

    protected void deleteResource(Resource resource) {
        if (resource._isLinkAvailable("resource:delete")) {
            try {
                resource.deleteResource();
            } catch (NotFoundException error) {}
        }
        // can't delete root resource
    }

    /**
     * Build the full resource path, including base path, add any missing leading '/', remove any trailing '/',
     * and remove any double '/'
     * @param basePath the base path
     * @param resourcePath the resource path
     * @return the full path
     */
    protected String buildResourcePath(String basePath, String resourcePath) {
        if (basePath == null) {
            basePath = "";
        }
        String base = trimSlashes(basePath);
        if (!base.equals("")) {
            base = "/" + base;
        }
        String result = StringUtils.removeEnd(base + "/" + trimSlashes(resourcePath), "/");
        if (result.equals("")) {
            result = "/";
        }
        return result;
    }

    private String trimSlashes(String path) {
        return StringUtils.removeEnd(StringUtils.removeStart(path, "/"), "/");
    }

    protected Resource createResource(RestApi api, String parentResourceId, String part, List<Resource> resources) {
        final Optional<Resource> existingResource = getResource(parentResourceId, part, resources);

        // create resource if doesn't exist
        if (!existingResource.isPresent()) {

            LOG.info("Creating resource '" + part + "' on " + parentResourceId);

            CreateResourceInput input = new CreateResourceInput();
            input.setPathPart(part);
            Resource resource = api.getResourceById(parentResourceId);

            Resource created = resource.createResource(input);

            resources.add(created);

            return created;
        } else {
            return existingResource.get();
        }
    }

    protected String getStringValue(Object in) {
        return in == null ? null : String.valueOf(in);  // use null value instead of "null"
    }

}
