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

import com.amazonaws.service.apigateway.importer.SwaggerApiImporter;
import com.amazonaws.service.apigateway.importer.impl.SchemaTransformer;
import com.amazonaws.services.apigateway.model.Integration;
import com.amazonaws.services.apigateway.model.IntegrationType;
import com.amazonaws.services.apigateway.model.Method;
import com.amazonaws.services.apigateway.model.MethodResponse;
import com.amazonaws.services.apigateway.model.Model;
import com.amazonaws.services.apigateway.model.PatchDocument;
import com.amazonaws.services.apigateway.model.PutIntegrationInput;
import com.amazonaws.services.apigateway.model.PutIntegrationResponseInput;
import com.amazonaws.services.apigateway.model.PutMethodInput;
import com.amazonaws.services.apigateway.model.PutMethodResponseInput;
import com.amazonaws.services.apigateway.model.Resource;
import com.amazonaws.services.apigateway.model.RestApi;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.inject.Inject;
import io.swagger.models.Operation;
import io.swagger.models.Path;
import io.swagger.models.RefModel;
import io.swagger.models.Response;
import io.swagger.models.Swagger;
import io.swagger.models.parameters.BodyParameter;
import io.swagger.models.parameters.Parameter;
import io.swagger.models.properties.Property;
import io.swagger.models.properties.RefProperty;
import io.swagger.util.Json;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static com.amazonaws.service.apigateway.importer.util.PatchUtils.createAddOperation;
import static com.amazonaws.service.apigateway.importer.util.PatchUtils.createPatchDocument;
import static com.amazonaws.service.apigateway.importer.util.PatchUtils.createRemoveOperation;
import static com.amazonaws.service.apigateway.importer.util.PatchUtils.createReplaceOperation;
import static java.lang.String.format;
import static java.util.Collections.emptyList;

public class ApiGatewaySdkSwaggerApiImporter extends ApiGatewaySdkApiImporter implements SwaggerApiImporter {

    private static final Log LOG = LogFactory.getLog(ApiGatewaySdkSwaggerApiImporter.class);
    private static final String DEFAULT_PRODUCES_CONTENT_TYPE = "application/json";
    private static final String EXTENSION_AUTH = "x-amazon-apigateway-auth";
    private static final String EXTENSION_INTEGRATION = "x-amazon-apigateway-integration";

    @Inject
    private Swagger swagger;

    @Override
    public String createApi(Swagger swagger, String name) {
        this.swagger = swagger;

        final RestApi api = createApi(getApiName(swagger, name), swagger.getInfo().getDescription());

        try {
            final Resource rootResource = getRootResource(api).get();
            deleteDefaultModels(api);
            createModels(api, swagger.getDefinitions(), swagger.getProduces());
            createResources(api, rootResource, swagger.getBasePath(), swagger.getProduces(), swagger.getPaths(), true);
        } catch (Throwable t) {
            LOG.error("Error creating API, rolling back", t);
            rollback(api);
            throw t;
        }
        return api.getId();
    }

    @Override
    public void updateApi(String apiId, Swagger swagger) {
        this.swagger = swagger;

        RestApi api = getApi(apiId);
        Optional<Resource> rootResource = getRootResource(api);

        updateModels(api, swagger.getDefinitions(), swagger.getProduces());
        updateResources(api, rootResource.get(), swagger.getBasePath(), swagger.getPaths(), swagger.getProduces());
        updateMethods(api, swagger.getBasePath(), swagger.getPaths(), swagger.getProduces());

        cleanupMethods(api, swagger.getBasePath(), swagger.getPaths());
        cleanupResources(api, swagger.getBasePath(), swagger.getPaths());
        cleanupModels(api, swagger.getDefinitions().keySet());
    }

    private String getApiName(Swagger swagger, String fileName) {
        String title = swagger.getInfo().getTitle();
        return StringUtils.isNotBlank(title) ? title : fileName;
    }

    private void createModels(RestApi api, Map<String, io.swagger.models.Model> definitions, List<String> produces) {
        if (definitions == null) {
            return;
        }

        for (Map.Entry<String, io.swagger.models.Model> entry : definitions.entrySet()) {
            final String modelName = entry.getKey();
            final io.swagger.models.Model model = entry.getValue();

            createModel(api, modelName, model, definitions, getProducesContentType(produces, emptyList()));
        }
    }

    private void createModel(RestApi api, String modelName, io.swagger.models.Model model, Map<String, io.swagger.models.Model> definitions, String modelContentType) {
        LOG.info(format("Creating model for api id %s with name %s", api.getId(), modelName));

        createModel(api, modelName, model.getDescription(), generateSchema(model, modelName, definitions), modelContentType);
    }

    private void createModel(RestApi api, String modelName, Property model, String modelContentType) {
        LOG.info(format("Creating model for api id %s with name %s", api.getId(), modelName));

        createModel(api, modelName, model.getDescription(), generateSchema(model, modelName, swagger.getDefinitions()), modelContentType);
    }

    private void updateMethods(RestApi api, String basePath, Map<String, Path> paths, List<String> apiProduces) {
        for (Map.Entry<String, Path> entry : paths.entrySet()) {
            final String fullPath = buildResourcePath(basePath, entry.getKey());

            final Path path = entry.getValue();

            final Map<String, Operation> ops = getOperations(path);

            for (Map.Entry<String, Operation> opEntry : ops.entrySet()) {
                final String httpMethod = opEntry.getKey();
                final Operation op = opEntry.getValue();

                // resolve the resource based on path - the resource is guaranteed to exist by this point
                final Resource resource = getResource(api, fullPath).get();

                String modelContentType = getProducesContentType(apiProduces, op.getProduces());

                if (methodExists(resource, httpMethod)) {
                    updateMethod(api, resource, httpMethod, op, modelContentType);
                } else {
                    createMethod(api, resource, httpMethod, op, modelContentType);
                }
            }
        }
    }

    private void createResources(RestApi api, Resource rootResource, String basePath, List<String> apiProduces, Map<String, Path> paths, boolean createMethods) {
        //build path tree

        for (Map.Entry<String, Path> entry : paths.entrySet()) {

            // create the resource tree
            Resource parentResource = rootResource;

            final String fullPath = buildResourcePath(basePath, entry.getKey());    // prepend the base path to all paths
            final String[] parts = fullPath.split("/");

            for (int i = 1; i < parts.length; i++) { // exclude root resource as this will be created when the api is created
                parentResource = createResource(api, parentResource.getId(), parts[i]);
            }

            if (createMethods) {
                // create methods on the leaf resource for each path
                createMethods(api, parentResource, entry.getValue(), apiProduces);
            }
        }
    }

    private void createMethods(final RestApi api, final Resource resource, Path path, List<String> apiProduces) {
        final Map<String, Operation> ops = getOperations(path);

        ops.entrySet().forEach(x -> {
            createMethod(api, resource, x.getKey(), x.getValue(),
                         getProducesContentType(apiProduces, x.getValue().getProduces()));
            LOG.info(format("Creating method for api id %s and resource id %s with method %s", api.getId(), resource.getId(), x.getKey()));
        });
    }

    private Map<String, Operation> getOperations(Path path) {
        final Map<String, Operation> ops = new HashMap<>();

        addOp(ops, "get", path.getGet());
        addOp(ops, "post", path.getPost());
        addOp(ops, "put", path.getPut());
        addOp(ops, "delete", path.getDelete());
        addOp(ops, "options", path.getOptions());
        addOp(ops, "patch", path.getPatch());

        return ops;
    }

    private void addOp(Map<String, Operation> ops, String method, Operation operation) {
        if (operation != null) {
            ops.put(method, operation);
        }
    }

    public void createMethod(RestApi api, Resource resource, String httpMethod,
                             Operation op, String modelContentType) {
        PutMethodInput input = new PutMethodInput();

        input.setAuthorizationType(getAuthorizationType(op));
        input.setApiKeyRequired(isApiKeyRequired(op));

        // set input model if present in body
        op.getParameters().stream().filter(p -> p.getIn().equals("body")).forEach(p -> {
            BodyParameter bodyParam = (BodyParameter) p;
            Optional<String> inputModel = getInputModel(bodyParam);

            input.setRequestModels(new HashMap<>());
            // model already imported
            if (inputModel.isPresent()) {
                LOG.info("Found input model reference " + inputModel.get());
                input.getRequestModels().put(modelContentType, inputModel.get());
            } else {
                // create new model from nested schema
                String modelName = generateModelName(bodyParam);
                LOG.info("Creating new model referenced from parameter: " + modelName);

                if (bodyParam.getSchema() == null) {
                    throw new IllegalArgumentException("Body parameter '" + bodyParam.getName() + "' must have a schema defined");
                }

                createModel(api, modelName, bodyParam.getSchema(), swagger.getDefinitions(), modelContentType);
                input.getRequestModels().put(modelContentType, modelName);
            }
        });

        // create method
        Method method = resource.putMethod(input, httpMethod.toUpperCase());

        createMethodResponses(api, method, modelContentType, op.getResponses());
        createMethodParameters(api, method, op.getParameters());
        createIntegration(method, op.getVendorExtensions());
    }

    private void createIntegration(Method method, Map<String, Object> vendorExtensions) {
        if (!vendorExtensions.containsKey(EXTENSION_INTEGRATION)) {
            return;
        }

        ObjectNode integ = (ObjectNode) vendorExtensions.get(EXTENSION_INTEGRATION);

        IntegrationType type = IntegrationType.valueOf(getStringValue(integ.get("type")).toUpperCase());

        LOG.info("Creating integration with type " + type);

        // todo: implement swagger parser for this extension
        Map<String, String> requestParameters = toMap((ObjectNode) integ.get("requestParameters"));
        Map<String, String> requestTemplates = toMap((ObjectNode) integ.get("requestTemplates"));
        List<String> cacheKeyParameters = toList((ArrayNode) integ.get("cacheKeyParameters"));

        PutIntegrationInput input = new PutIntegrationInput()
                .withType(type)
                .withUri(getStringValue(integ.get("uri")))
                .withCredentials(getStringValue(integ.get("credentials")))
                .withHttpMethod((getStringValue(integ.get("httpMethod"))))
                .withCacheNamespace(getStringValue(integ.get("cacheNamespace")))
                .withRequestParameters(requestParameters)
                .withRequestTemplates(requestTemplates)
                .withCacheKeyParameters(cacheKeyParameters);

        Integration integration = method.putIntegration(input);

        createIntegrationResponses(integration, integ);
    }

    private void createIntegrationResponses(Integration integration, ObjectNode integ) {
        Iterator<Map.Entry<String, JsonNode>> it = integ.get("responses").fields();

        while (it.hasNext()) {

            Map.Entry<String, JsonNode> field = it.next();
            String key = field.getKey();
            ObjectNode response = (ObjectNode) field.getValue();

            String pattern = key.equals("default") ? null : key;
            String status = response.get("statusCode").asText();

            Map<String, String> responseParameters = toMap((ObjectNode) response.get("responseParameters"));
            Map<String, String> responseTemplates = toMap((ObjectNode) response.get("responseTemplates"));

            PutIntegrationResponseInput input = new PutIntegrationResponseInput()
                    .withResponseParameters(responseParameters)
                    .withResponseTemplates(responseTemplates)
                    .withSelectionPattern(pattern);

            integration.putIntegrationResponse(input, status);
        }
    }

    private String getAuthorizationType(Operation op) {
        // currently only Sigv4 supported by service
        return containsSecurity(op, "sigv4") || containsAuthExtension(op, EXTENSION_AUTH) ? "AWS_IAM" : "NONE";
    }

    private boolean containsAuthExtension(Operation op, String extensionAuth) {
        if (op.getVendorExtensions() != null) {
            Optional<Map.Entry<String, Object>> vendorExtension = op.getVendorExtensions().entrySet().stream().filter(e -> extensionAuth.equals(e.getKey())).findFirst();
            if (!vendorExtension.isPresent()) {
                return false;
            }

            ObjectNode type = (ObjectNode) vendorExtension.get().getValue();
            return "aws_iam".equals(type.get("type").textValue());
        }

        return false;
    }

    private Boolean isApiKeyRequired(Operation op) {
        return containsSecurity(op, "api_key");
    }

    private Boolean containsSecurity(Operation op, String securityDefinitionName) {
        // enabled on operation level
        if (op.getSecurity() != null) {
            boolean opRequires = op.getSecurity().stream().anyMatch(s -> s.containsKey(securityDefinitionName));
            if (opRequires) {
                return true;
            }
        }

        // enabled on API level
        return swagger.getSecurity() != null && swagger.getSecurity().stream().anyMatch(s -> s.getRequirements().containsKey(securityDefinitionName));
    }

    private String generateSchema(Property model, String modelName, Map<String, io.swagger.models.Model> definitions) {
        return generateSchemaString(model, modelName, definitions);
    }

    private String generateSchemaString(Object model, String modelName, Map<String, io.swagger.models.Model> definitions) {
        try {
            String modelSchema = Json.mapper().writeValueAsString(model);
            String models = Json.mapper().writeValueAsString(definitions);

            // inline all references
            String schema = new SchemaTransformer().flatten(modelSchema, models);

            LOG.info("Generated json-schema for model " + modelName + ": " + schema);

            return schema;
        } catch (IOException e) {
            throw new IllegalArgumentException("Could not process model", e);
        }
    }

    private String generateSchema(io.swagger.models.Model model, String modelName, Map<String, io.swagger.models.Model> definitions) {
        return generateSchemaString(model, modelName, definitions);
    }

    private Optional<String> getInputModel(BodyParameter p) {
        io.swagger.models.Model model = p.getSchema();

        if (model instanceof RefModel) {
            String modelName = ((RefModel) model).getSimpleRef();   // assumption: complex ref?
            return Optional.of(modelName);
        }

        return Optional.empty();
    }

    String generateModelName(Response response) {
        return generateModelName(response.getDescription());
    }

    private String generateModelName(String description) {
        if (StringUtils.isBlank(description)) {
            LOG.warn("No description found for model, will generate a unique model name");
            return "model" + UUID.randomUUID().toString().substring(0, 8);
        }

        // note: generating model name based on sanitized description
        return description.replaceAll(getModelNameSanitizeRegex(), "");
    }

    private String generateModelName(BodyParameter param) {
        return generateModelName(param.getDescription());
    }

    private String getModelNameSanitizeRegex() {
        return "[^A-Za-z0-9]";
    }

    private void updateResources(RestApi api, Resource rootResourceId, String basePath, Map<String, Path> paths, List<String> apiProduces) {
        createResources(api, rootResourceId, basePath, apiProduces, paths, false);
    }

    private void updateModels(RestApi api, Map<String, io.swagger.models.Model> definitions, List<String> apiProduces) {
        if (definitions == null) {
            return;
        }

        for (Map.Entry<String, io.swagger.models.Model> entry : definitions.entrySet()) {
            final String modelName = entry.getKey();
            final io.swagger.models.Model model = entry.getValue();

            if (getModel(api, modelName).isPresent()) {
                updateModel(api, modelName, model);
            } else {
                createModel(api, modelName, model, definitions, getProducesContentType(apiProduces, emptyList()));
            }
        }
    }

    private void updateModel(RestApi api, String modelName, io.swagger.models.Model model) {
        LOG.info(format("Updating model for api id %s and model name %s", api.getId(), modelName));
        updateModel(api, modelName, generateSchema(model, modelName, swagger.getDefinitions()));
    }

    private void updateMethod(RestApi api, Resource resource, String httpMethod, Operation op, String modelContentType) {
        LOG.info(format("Updating method for api id %s and resource %s and method %s", api.getId(), resource.getId(), httpMethod));

        PatchDocument pd = createPatchDocument(
                createReplaceOperation("/authorizationType", getAuthorizationType(op)),
                createReplaceOperation("/apiKeyRequired", String.valueOf((boolean) isApiKeyRequired(op))));
        Method method = resource.getMethodByHttpMethod(httpMethod.toUpperCase()).updateMethod(pd);

        updateMethodResponses(api, method, modelContentType, op.getResponses());
        updateMethodParameters(api, method, op.getParameters());
        createIntegration(method, op.getVendorExtensions());
    }

    private void cleanupMethods(RestApi api, String basePath, Map<String, Path> paths) {
        LOG.info("Cleaning up removed methods");

        for (Resource r : buildResourceList(api)) {
            for (Method m : r.getResourceMethods().values()) {
                String httpMethod = m.getHttpMethod().toLowerCase();

                if (!isMethodInSwagger(r.getPath(), httpMethod, basePath, paths)) {
                    LOG.info(format("Removing deleted method %s for resource %s", httpMethod, r.getId()));

                    m.deleteMethod();
                }
            }
        }
    }

    private boolean isMethodInSwagger(String path, String httpMethod, String basePath, Map<String, Path> paths) {
        for (Map.Entry<String, Path> entry : paths.entrySet()) {

            Map<String, Operation> ops = getOperations(entry.getValue());

            String fullPath = buildResourcePath(basePath, entry.getKey());

            if (fullPath.equals(path) && ops.containsKey(httpMethod)) {
                return true;
            }
        }
        return false;
    }

    private void cleanupResources(RestApi api, String basePath, Map<String, Path> paths) {
        cleanupResources(api, buildResourceSet(paths.keySet(), basePath));
    }

    private Set<String> buildResourceSet(Set<String> paths, String basePath) {
        if (StringUtils.isBlank(basePath)) {
            basePath = "/";
        }

        Set<String> resourceSet = new HashSet<>();
        for (String path : paths) {
            resourceSet.addAll(Arrays.asList(path.split("/")));
        }
        resourceSet.addAll(Arrays.asList(basePath.split("/")));
        return resourceSet;
    }

    private PutMethodResponseInput getCreateResponseInput(RestApi api, String modelContentType, Response response) {

        final PutMethodResponseInput input = new PutMethodResponseInput();

        // add response headers
        if (response.getHeaders() != null) {
            input.setResponseParameters(new HashMap<>());
            response.getHeaders().entrySet().forEach(
                    e -> input.getResponseParameters().put("method.response.header." + e.getKey(), e.getValue().getRequired()));
        }

        // if the schema references an existing model, use that model for the response
        Optional<Model> modelOpt = getModel(api, response);
        if (modelOpt.isPresent()) {
            input.setResponseModels(new HashMap<>());
            input.getResponseModels().put(modelContentType, modelOpt.get().getName());
            LOG.info("Found reference to existing model " + modelOpt.get().getName());
        } else {
            // generate a model based on the schema if the model doesn't already exist
            if (response.getSchema() != null) {
                String modelName = generateModelName(response);

                LOG.info("Creating new model referenced from response: " + modelName);

                createModel(api, modelName, response.getSchema(), modelContentType);

                input.setResponseModels(new HashMap<>());
                input.getResponseModels().put(modelContentType, modelName);
            }
        }

        return input;
    }

    private void createMethodResponses(RestApi api, Method method, String modelContentType, Map<String, Response> responses) {
        if (responses == null) {
            return;
        }

        // add responses from swagger
        responses.entrySet().forEach(e -> {
            if (e.getKey().equals("default")) {
                LOG.warn("Default response not supported, skipping");
            } else {
                LOG.info(format("Creating method response for api %s and method %s and status %s",
                                api.getId(), method.getHttpMethod(), e.getKey()));

                method.putMethodResponse(getCreateResponseInput(api, modelContentType, e.getValue()), e.getKey());
            }
        });
    }

    /*
     * Get the model referenced by given schema if it exists
     */
    private Optional<Model> getModel(RestApi api, Response response) {

        String modelName;

        // if the response references a proper model, look for a model matching the model name
        if (response.getSchema() != null && response.getSchema().getType().equals("ref")) {
            modelName = ((RefProperty) response.getSchema()).getSimpleRef();
        } else {
            // if the response has an embedded schema, look for a model matching the generated name
            modelName = generateModelName(response);
        }

        try {
            return Optional.of(api.getModelByName(modelName));
        } catch (Exception ignored) {}

        return Optional.empty();
    }

    private void createMethodParameters(RestApi api, Method method, List<Parameter> parameters) {
        parameters.forEach(p -> {
            if (!p.getIn().equals("body")) {
                if (getParameterLocation(p).isPresent()) {
                    String expression = createRequestParameterExpression(p);

                    LOG.info(format("Creating method parameter for api %s and method %s with name %s",
                                    api.getId(), method.getHttpMethod(), expression));

                    method.updateMethod(createPatchDocument(createAddOperation("/requestParameters/" + expression,
                                                                               String.valueOf(p.getRequired()))));
                }
            }
        });
    }

    private String createRequestParameterExpression(Parameter p) {
        Optional<String> loc = getParameterLocation(p);
        return "method.request." + loc.get() + "." + p.getName();
    }

    private Optional<String> getParameterLocation(Parameter p) {
        switch (p.getIn()) {
            case "path":
                return Optional.of("path");
            case "query":
                return Optional.of("querystring");
            case "header":
                return Optional.of("header");
            default:
                LOG.warn("Parameter type " + p.getIn() + " not supported, skipping");
                break;
        }
        return Optional.empty();
    }

    private void updateMethodParameters(RestApi api, Method method, List<Parameter> parameters) {
        // clear existing params
        if (method.getRequestParameters() != null) {
            method.getRequestParameters().keySet().forEach(
                    k -> method.updateMethod(createPatchDocument(createRemoveOperation("/requestParameters/" + k))));
        }

        // add all params from swaqgger
        createMethodParameters(api, method, parameters);
    }

    private void updateMethodResponses(RestApi api, Method method, String modelContentType, Map<String, Response> responses) {
        Map<String, MethodResponse> responseMap = method.getMethodResponses();

        // delete all existing responses
        responseMap.values().forEach(MethodResponse::deleteMethodResponse);
        createMethodResponses(api, method, modelContentType, responses);
    }

    /*
     * Get the content-type to use for models and responses based on the method "produces" or the api "produces" content-types
     *
     * First look in the method produces and favor application/json, otherwise return the first method produces type
     * If no method produces, fall back to api produces and favor application/json, otherwise return the first api produces type
     * If no produces are defined on the method or api, default to application/json
     */
    // todo: check this logic for apis/methods producing multiple content-types
    // note: assumption - models in an api will always use one of the api "produces" content types, favoring application/json. models created from operation responses may use the operation "produces" content type
    private String getProducesContentType(List<String> apiProduces, List<String> methodProduces) {

        if (methodProduces != null && !methodProduces.isEmpty()) {
            if (methodProduces.stream().anyMatch(t -> t.equalsIgnoreCase(DEFAULT_PRODUCES_CONTENT_TYPE))) {
                return DEFAULT_PRODUCES_CONTENT_TYPE;
            }

            return methodProduces.get(0);
        }

        if (apiProduces != null && !apiProduces.isEmpty()) {
            if (apiProduces.stream().anyMatch(t -> t.equalsIgnoreCase(DEFAULT_PRODUCES_CONTENT_TYPE))) {
                return DEFAULT_PRODUCES_CONTENT_TYPE;
            }

            return apiProduces.get(0);
        }

        return DEFAULT_PRODUCES_CONTENT_TYPE;
    }

    private void cleanupResources(RestApi api, Set<String> paths) {
        LOG.info("Cleaning up removed resources");

        // don't remove the resource if it's path part exists in any of the swagger paths
        // this prevents intermediate resources from being deleted, but may also prevent deletion when resources are "moved"
        buildResourceList(api).stream().filter(resource -> !paths.contains(resource.getPathPart()) && !resource.getPath().equals("/"))
                .forEach(resource -> {
                    LOG.info("Removing deleted resource " + resource.getPath());
                    deleteResource(resource);
                });
    }

    private List<String> toList(ArrayNode node) {
        if (node == null) {
            return Collections.emptyList();
        }

        List<String> cacheKeyParameters = new ArrayList<>();
        for (JsonNode child : node) {
            cacheKeyParameters.add(child.asText());
        }
        return cacheKeyParameters;
    }

    private Map<String, String> toMap(ObjectNode node) {
        if (node == null) {
            return Collections.emptyMap();
        }

        Map<String, String> map = new HashMap<>();
        Iterator<Map.Entry<String, JsonNode>> it = node.fields();
        while (it.hasNext()) {
            Map.Entry<String, JsonNode> field = it.next();
            map.put(field.getKey(), field.getValue().asText());
        }
        return map;
    }

}
