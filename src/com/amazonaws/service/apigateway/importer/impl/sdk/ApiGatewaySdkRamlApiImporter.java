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

import com.amazonaws.service.apigateway.importer.RamlApiImporter;
import com.amazonaws.services.apigateway.model.Integration;
import com.amazonaws.services.apigateway.model.IntegrationType;
import com.amazonaws.services.apigateway.model.Method;
import com.amazonaws.services.apigateway.model.MethodResponse;
import com.amazonaws.services.apigateway.model.PatchDocument;
import com.amazonaws.services.apigateway.model.PutIntegrationInput;
import com.amazonaws.services.apigateway.model.PutIntegrationResponseInput;
import com.amazonaws.services.apigateway.model.PutMethodInput;
import com.amazonaws.services.apigateway.model.PutMethodResponseInput;
import com.amazonaws.services.apigateway.model.Resource;
import com.amazonaws.services.apigateway.model.RestApi;
import com.amazonaws.util.json.JSONArray;
import com.amazonaws.util.json.JSONException;
import com.amazonaws.util.json.JSONObject;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.raml.model.Action;
import org.raml.model.ActionType;
import org.raml.model.MimeType;
import org.raml.model.Raml;
import org.raml.model.Response;
import org.raml.model.parameter.Header;
import org.raml.model.parameter.QueryParameter;
import org.raml.model.parameter.UriParameter;

import javax.annotation.Nullable;
import java.util.ArrayList;
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

public class ApiGatewaySdkRamlApiImporter extends ApiGatewaySdkApiImporter implements RamlApiImporter {

    private static final Log LOG = LogFactory.getLog(ApiGatewaySdkRamlApiImporter.class);

    private JSONObject config;

    // NOTE: This is the only shared state - is there a better approach? Why wasn't this used until now? Can this be
    // reused in the swagger implementation?
    private Set<String> models = new HashSet<>();
    private Set<String> paths = new HashSet<>();

    @Override
    public String createApi(Raml raml, String name, JSONObject config) {
        this.config = config;

        // TODO: What to use as description?
        final RestApi api = createApi(getApiName(raml, name), null);

        LOG.info("Created API "+api.getId());
        
        try {
            final Resource rootResource = getRootResource(api).get();
            deleteDefaultModels(api);
            createModels(api, raml.getSchemas(), false);
            createResources(api, createResourcePath(api, rootResource, raml.getBasePath()), raml.getResources(), false);
        } catch (Throwable t) {
            LOG.error("Error creating API, rolling back", t);
            rollback(api);
            throw t;
        }
        return api.getId();
    }

    @Override
    public void updateApi(String apiId, Raml raml, JSONObject config) {
        this.config = config;

        RestApi api = getApi(apiId);
        Optional<Resource> rootResource = getRootResource(api);

        createModels(api, raml.getSchemas(), true);
        createResources(api, createResourcePath(api, rootResource.get(), raml.getBasePath()), raml.getResources(), true);

        cleanupResources(api, this.paths);
        cleanupModels(api, this.models);
    }

    private String getApiName (Raml raml, String fileName) {
        String title = raml.getTitle();
        return StringUtils.isNotBlank(title) ? title : fileName;
    }

    private void createModels(RestApi api, List<Map<String, String>> schemas, boolean update) {
        for (Map<String, String> entries : schemas) {
            for (Map.Entry<String, String> entry : entries.entrySet()) {
                final String schemaName = entry.getKey();
                final String schemaValue = entry.getValue();

                models.add(schemaName);

                if (update && getModel(api, schemaName).isPresent()) {
                    updateModel(api, schemaName, schemaValue);
                } else {
                    createModel(api, schemaName, schemaValue);
                }
            }
        }
    }

    private void createModel(RestApi api, String schemaName, String schemaValue) {
        // HACK: Attempt to detect JSON/XML bodies.
        final Integer openTagIndex = schemaValue.indexOf('<');
        final Integer openJsonIndex = schemaValue.indexOf('{');

        // Is this possible or is the parser validating schemas?
        if (openTagIndex == openJsonIndex) {
            return;
        }

        final boolean isJson = openJsonIndex > -1 && (openTagIndex == -1 || openJsonIndex < openTagIndex);

        // TODO: What to put as description?
        createModel(api, schemaName, null, schemaValue, isJson ? "application/json" : "text/xml");
    }

    private void createResources(RestApi api, Resource rootResource, Map<String, org.raml.model.Resource> resources, boolean update) {
        for (Map.Entry<String, org.raml.model.Resource> entry : resources.entrySet()) {
            final org.raml.model.Resource resource = entry.getValue();
            final Resource parentResource = createResourcePath(api, rootResource, entry.getKey());

            createMethods(api, parentResource, resource.getActions(), update);
            createResources(api, parentResource, resource.getResources(), update);
        }
    }

    private void cleanupResources(RestApi api, Set<String> paths) {
        buildResourceList(api)
                .stream()
                .filter(resource -> !resource.getPath().equals("/") && !paths.contains(resource.getPath()))
                .forEach(resource -> {
                    LOG.info("Removing deleted resource " + resource.getPath());
                    deleteResource(resource);
                });
    }

    private Resource createResourcePath(RestApi api, Resource resource, String fullPath) {
        final String[] parts = fullPath.split("/");

        Resource parentResource = resource;

        List<Resource> resources = buildResourceList(api);

        for (int i = 1; i < parts.length; i++) {
            parentResource = createResource(api, parentResource.getId(), parts[i], resources);

            paths.add(parentResource.getPath());
        }

        return parentResource;
    }

    private void createMethods(RestApi api, Resource resource, Map<ActionType, Action> actions, boolean update) {
        for (Map.Entry<ActionType, Action> entry : actions.entrySet()) {
            createMethod(api, resource, entry.getKey(), entry.getValue(), update);
        }

        if (update) {
            cleanupMethods(resource, actions);
        }
    }

    private void cleanupMethods (Resource resource, Map<ActionType, Action> actions) {
        final HashSet<String> methods = new HashSet<>();

        for (ActionType action : actions.keySet()) {
            methods.add(action.toString());
        }

        for (Method m : resource.getResourceMethods().values()) {
            String httpMethod = m.getHttpMethod().toUpperCase();

            if (!methods.contains(httpMethod)) {
                LOG.info(format("Removing deleted method %s for resource %s", httpMethod, resource.getId()));

                m.deleteMethod();
            }
        }
    }

    private void createMethod(final RestApi api, final Resource resource, final ActionType httpMethod, final Action action, boolean update) {
        Method method;

        if (update && methodExists(resource, httpMethod.toString())) {
            method = resource.getMethodByHttpMethod(httpMethod.toString());

            PatchDocument pd = createPatchDocument(
                    createReplaceOperation("/authorizationType", getAuthorizationTypeFromConfig(resource, httpMethod.toString(), this.config)),
                    createReplaceOperation("/apiKeyRequired", "false"));

            method.updateMethod(pd);

            if (action.hasBody()) {
                for (Map.Entry<String, MimeType> entry : action.getBody().entrySet()) {
                    final String mime = entry.getKey();
                    final String modelName = createModel(api, mime, entry.getValue());

                    if (modelName != null) {
                        method.updateMethod(createPatchDocument(createAddOperation(
                                "/requestModels/" + escapeOperationString(mime), modelName
                        )));
                    }
                }
            }

            cleanupMethodModels(method, action.getBody());
        } else {
            LOG.info(format("Creating method for api id %s and resource id %s with method %s", api.getId(), resource.getId(), httpMethod));

            PutMethodInput input = new PutMethodInput();

            // TODO: Figure out API key.
            input.setApiKeyRequired(false);
            input.setAuthorizationType(getAuthorizationTypeFromConfig(resource, httpMethod.toString(), this.config));
            input.setRequestModels(new HashMap<>());

            if (action.hasBody()) {
                for (Map.Entry<String, MimeType> entry : action.getBody().entrySet()) {
                    final String mime = entry.getKey();
                    final String modelName = createModel(api, mime, entry.getValue());

                    if (modelName != null) {
                        input.getRequestModels().put(mime, modelName);
                    }
                }
            }

            method = resource.putMethod(input, httpMethod.toString());
        }

        for (Map.Entry<String, UriParameter> entry : action.getResource().getUriParameters().entrySet()) {
            updateMethod(api, method, "path", entry.getKey(), entry.getValue().isRequired());
        }

        for (Map.Entry<String, Header> entry : action.getHeaders().entrySet()) {
            updateMethod(api, method, "header", entry.getKey(), entry.getValue().isRequired());
        }

        for (Map.Entry<String, QueryParameter> entry : action.getQueryParameters().entrySet()) {
            updateMethod(api, method, "querystring", entry.getKey(), entry.getValue().isRequired());
        }

        if (update) {
            cleanupMethod(method, "path", action.getResource().getUriParameters().keySet());
            cleanupMethod(method, "header", action.getHeaders().keySet());
            cleanupMethod(method, "querystring", action.getQueryParameters().keySet());
        }

        createIntegration(resource, method, this.config);

        createMethodResponses(api, method, action.getResponses(), update);
    }

    private void createIntegration(Resource resource, Method method, JSONObject config) {
        if (config == null) {
            return;
        }

        try {
            final JSONObject integ = config.getJSONObject(resource.getPath())
                    .getJSONObject(method.getHttpMethod().toLowerCase())
                    .getJSONObject("integration");

            IntegrationType type = IntegrationType.valueOf(integ.getString("type").toUpperCase());

            LOG.info("Creating integration with type " + type);

            PutIntegrationInput input = new PutIntegrationInput()
                    .withType(type)
                    .withUri(integ.getString("uri"))
                    .withCredentials(integ.optString("credentials"))
                    .withHttpMethod(integ.optString("httpMethod"))
                    .withRequestParameters(jsonObjectToHashMapString(integ.optJSONObject("requestParameters")))
                    .withRequestTemplates(jsonObjectToHashMapString(integ.optJSONObject("requestTemplates")))
                    .withCacheNamespace(integ.optString("cacheNamespace"))
                    .withCacheKeyParameters(jsonObjectToListString(integ.optJSONArray("cacheKeyParameters")));

            Integration integration = method.putIntegration(input);

            createIntegrationResponses(integration, integ.optJSONObject("responses"));
        } catch (JSONException e) {
            LOG.info(format("Skipping integration for method %s of %s: %s", method.getHttpMethod(), resource.getPath(), e));
        }
    }

    private void createIntegrationResponses(Integration integration, JSONObject responses) {
        if (responses == null) {
            return;
        }

        final Iterator<String> keysIterator = responses.keys();

        while (keysIterator.hasNext()) {
            String key = keysIterator.next();

            try {
                String pattern = key.equals("default") ? null : key;
                JSONObject response = responses.getJSONObject(key);

                String status = (String) response.get("statusCode");

                PutIntegrationResponseInput input = new PutIntegrationResponseInput()
                        .withResponseParameters(jsonObjectToHashMapString(response.optJSONObject("responseParameters")))
                        .withResponseTemplates(jsonObjectToHashMapString(response.optJSONObject("responseTemplates")))
                        .withSelectionPattern(pattern);

                integration.putIntegrationResponse(input, status);
            } catch (JSONException e) {
            }
        }
    }

    private List<String> jsonObjectToListString (JSONArray json) {
        if (json == null) {
          return null;
        }

        final List<String> list = new ArrayList<>();

        for (int i = 0; i < json.length(); i++) {
            try {
                list.add(json.getString(i));
            } catch (JSONException e) {}
        }

        return list;
    }

    private Map<String, String> jsonObjectToHashMapString (JSONObject json) {
        if (json == null) {
          return null;
        }

        final Map<String, String> map = new HashMap<>();
        final Iterator<String> keysIterator = json.keys();

        while (keysIterator.hasNext()) {
            String key = keysIterator.next();

            try {
                map.put(key, json.getString(key));
            } catch (JSONException e) {}
        }

        return map;
    }

    private void cleanupMethodModels(Method method, Map<String, MimeType> body) {
        if (method.getRequestModels() != null) {
            for (Map.Entry<String, String> entry : method.getRequestModels().entrySet()) {
                if (!body.containsKey(entry.getKey()) || body.get(entry.getKey()).getSchema() == null) {
                    LOG.info(format("Removing model %s from method %s", entry.getKey(), method.getHttpMethod()));

                    method.updateMethod(createPatchDocument(createRemoveOperation("/requestModels/" + escapeOperationString(entry.getKey()))));
                }
            }
        }
    }

    private void cleanupMethod(Method method, String type, Set<String> parameterSet) {
        if (method.getRequestParameters() != null) {
            method.getRequestParameters().keySet().forEach(key -> {
                final String[] parts = key.split("\\.");
                final String paramType = parts[2];
                final String paramName = parts[3];

                if (paramType.equals(type) && !parameterSet.contains(paramName)) {
                    method.updateMethod(createPatchDocument(createRemoveOperation("/requestParameters/" + key)));
                }
            });
        }
    }

    private String generateModelName() {
        return "model" + UUID.randomUUID().toString().substring(0, 8);
    }

    private void createMethodResponses(RestApi api, Method method, Map<String, Response> responses, boolean update) {
        for (Map.Entry<String, Response> entry : responses.entrySet()) {
            createMethodResponse(api, method, entry.getKey(), entry.getValue(), update);
        }

        if (update) {
            cleanupMethodResponses(method, responses);
        }
    }

    private void cleanupMethodResponses(Method method, Map<String, Response> responses) {
        method.getMethodResponses().entrySet().forEach(entry -> {
            if (!responses.containsKey(entry.getKey())) {
                entry.getValue().deleteMethodResponse();
            }
        });
    }

    private void createMethodResponse(RestApi api, Method method, String statusCode, Response response, boolean update) {
        // TODO: Improve implementation by patching.
        if (update && method.getMethodResponses().containsKey(statusCode)) {
            final MethodResponse methodResponse = method.getMethodResponses().get(statusCode);

            methodResponse.deleteMethodResponse();
        }

        final PutMethodResponseInput input = new PutMethodResponseInput();

        input.setResponseModels(new HashMap<>());
        input.setResponseParameters(new HashMap<>());

        for (Map.Entry<String, Header> entry : response.getHeaders().entrySet()) {
            input.getResponseParameters().put(escapeOperationString("method.response.header." + entry.getKey()), entry.getValue().isRequired());
        }

        if (response.hasBody()) {
            for (Map.Entry<String, MimeType> entry : response.getBody().entrySet()) {
                final String mime = entry.getKey();
                final String modelName = createModel(api, mime, entry.getValue());

                if (modelName != null) {
                    input.getResponseModels().put(mime, modelName);
                }
            }
        }

        method.putMethodResponse(input, statusCode);
    }

    @Nullable
    private String createModel(RestApi api, String mime, MimeType mimeType) {
        final String schema = mimeType.getSchema();

        if (schema != null) {
            if (schema.matches("\\w+")) {
                return schema;
            }

            final String modelName = generateModelName();

            models.add(modelName);
            createModel(api, modelName, null, schema, mime);

            return modelName;
        }

        return null;
    }

    private String getExpression(String area, String part, String type, String name) {
        return area + "." + part + "." + type + "." + name;
    }

    private void updateMethod(RestApi api, Method method, String type, String name, boolean required) {
        String expression = getExpression("method", "request", type, name);
        Map<String, Boolean> requestParameters = method.getRequestParameters();
        Boolean requestParameter = requestParameters == null ? null : requestParameters.get(expression);

        if (requestParameter != null && requestParameter.equals(required)) {
            return;
        }

        LOG.info(format("Creating method parameter for api %s and method %s with name %s",
                        api.getId(), method.getHttpMethod(), expression));

        method.updateMethod(createPatchDocument(createAddOperation("/requestParameters/" + expression, getStringValue(required))));
    }

    private String getAuthorizationTypeFromConfig(Resource resource, String method, JSONObject config) {
        if (config == null) {
            return "NONE";
        }

        try {
            return config.getJSONObject(resource.getPath())
                    .getJSONObject(method.toLowerCase())
                    .getJSONObject("auth")
                    .getString("type")
                    .toUpperCase();
        } catch (JSONException exception) {
            return "NONE";
        }
    }

    private String escapeOperationString(String value) {
        return value.replaceAll("~", "~0").replaceAll("/", "~1");
    }


}
