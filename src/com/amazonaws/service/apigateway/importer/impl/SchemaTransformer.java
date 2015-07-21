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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.github.fge.jsonschema.core.exceptions.ProcessingException;
import com.github.fge.jsonschema.core.report.ProcessingReport;
import com.github.fge.jsonschema.main.JsonSchemaFactory;
import org.apache.log4j.Logger;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Deserializes and transforms schema schemas into normalized form
 *
 * @author rpgreen
 */
public class SchemaTransformer {
    protected final static Logger LOG = Logger.getLogger(SchemaTransformer.class);

    /**
     * Get a schema schema in "flattened" form whereby all dependent references are resolved
     * and included as inline schema definitions
     *
     * @return the json-schema string in flattened form
     */
    public String flatten(String model, String models) {
        return getFlattened(deserialize(model), deserialize(models));
    }

    private void buildSchemaReferenceMap(JsonNode model, JsonNode models, Map<String, String> modelMap) {
        Map<JsonNode, JsonNode> refs = new HashMap<>();
        findReferences(model, refs);

        for (JsonNode ref : refs.keySet()) {
            String canonicalRef = ref.textValue();

            String schemaName = getSchemaName(canonicalRef);

            JsonNode subSchema = getSchema(schemaName, models);

            // replace reference values with inline definitions
            replaceRef((ObjectNode) refs.get(ref), schemaName);

            buildSchemaReferenceMap(subSchema, models, modelMap);

            modelMap.put(schemaName, serializeExisting(subSchema));
        }
    }

    private JsonNode getSchema(String schemaName, JsonNode models) {
        return models.findPath(schemaName);
    }

    private String getFlattened(JsonNode model, JsonNode models) {
        HashMap<String, String> schemaMap = new HashMap<>();

        buildSchemaReferenceMap(model, models, schemaMap);

        replaceRefs(model, schemaMap);

        if (LOG.isTraceEnabled()) {
            try {
                LOG.trace("Flattened schema to: " + new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(model));
            } catch (JsonProcessingException ignored){}
        }

        String flattened = serializeExisting(model);

        validate(model);

        return flattened;
    }

    private void validate(JsonNode rootNode) {
        final JsonSchemaFactory factory;
        try {
            factory = JsonSchemaFactory.byDefault();
            factory.getJsonSchema(rootNode);
        } catch (ProcessingException e) {
            throw new IllegalStateException("Invalid schema json was generated", e);
        } catch (ExceptionInInitializerError | NoClassDefFoundError e) {
            return; // this should only happen from test code. JsonSchemaFactory not easily mocked
        }

        ProcessingReport report = factory.getSyntaxValidator().validateSchema(rootNode);
        if (!report.isSuccess()) {
            throw new IllegalStateException("Invalid schema json was generated" + report.iterator().next().getMessage());
        }
    }

    /*
     * Add schema references as inline definitions to the root schema
     */
    private void replaceRefs(JsonNode root, HashMap<String, String> schemaMap) {

        ObjectNode definitionsNode = new ObjectNode(JsonNodeFactory.instance);

        for (Map.Entry<String, String> entry : schemaMap.entrySet()) {
            JsonNode schemaNode = deserialize(entry.getValue());
            definitionsNode.set(entry.getKey(), schemaNode);
        }

        ((ObjectNode)root).set("definitions", definitionsNode);
    }

    /*
     * Replace a reference node with an inline reference
     */
    private void replaceRef(ObjectNode parent, String schemaName) {
        parent.set("$ref", new TextNode("#/definitions/" + schemaName));
    }

    /*
     * Find all reference node in the schema tree. Build a map of the reference node to its parent
     */
    private void findReferences(JsonNode node, Map<JsonNode, JsonNode> refNodes) {
        JsonNode refNode = node.path("$ref");
        if (!refNode.isMissingNode()) {
            refNodes.put(refNode, node);
        }

        for (JsonNode child : node) {
            findReferences(child, refNodes);
        }
    }

    /*
    * Attempt to serialize an existing schema
    * If this fails something is seriously wrong, because this schema has already been saved by the control plane
    */
    JsonNode deserialize(String schemaText) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            return mapper.readTree(schemaText);
        } catch (IOException e) {
            throw new IllegalStateException("Invalid schema found. Could not deserialize schema: " + schemaText, e);
        }
    }

    /*
     * Attempt to serialize an existing schema
     * If this fails something is seriously wrong, because this schema has already been saved by the control plane
     */
    private String serializeExisting(JsonNode root) {
        try {
            return new ObjectMapper().writeValueAsString(root);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialize generated schema json", e);
        }
    }

    public static String getSchemaName(String refVal) {
        String schemaName;
        try {
            schemaName = refVal.substring(refVal.lastIndexOf("/") + 1,
                                          refVal.length());
        } catch (Throwable t) {
            throw new IllegalStateException("Invalid reference found: " + refVal, t);
        }

        return schemaName;
    }

    public static String getRestApiId(String refVal) {
        String apiId;
        try {
            apiId = refVal.substring(refVal.indexOf("restapis/"),
                                     refVal.length()).split("/")[1];
        } catch (Throwable t) {
            throw new IllegalStateException("Invalid reference found: " + refVal, t);
        }

        return apiId;
    }

}
