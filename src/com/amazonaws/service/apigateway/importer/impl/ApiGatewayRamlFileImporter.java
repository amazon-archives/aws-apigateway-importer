package com.amazonaws.service.apigateway.importer.impl;

import com.amazonaws.service.apigateway.importer.RamlApiFileImporter;
import com.amazonaws.service.apigateway.importer.RamlApiImporter;
import com.amazonaws.util.json.JSONObject;
import com.google.inject.Inject;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.raml.model.Raml;
import org.raml.parser.visitor.RamlDocumentBuilder;

import java.io.File;

import static java.lang.String.format;

public class ApiGatewayRamlFileImporter implements RamlApiFileImporter {
    private static final Log LOG = LogFactory.getLog(ApiGatewayRamlFileImporter.class);

    private final RamlDocumentBuilder builder;
    private final RamlApiImporter client;

    @Inject
    public ApiGatewayRamlFileImporter(RamlDocumentBuilder builder, RamlApiImporter client) {
        this.builder = builder;
        this.client = client;
    }

    @Override
    public String importApi(String filePath, JSONObject config) {
        LOG.info(format("Attempting to create API from RAML definition. " +
                "RAML file: %s", filePath));

        final Raml raml = parse(filePath);

        return client.createApi(raml, new File(filePath).getName(), config);
    }

    @Override
    public void updateApi(String apiId, String filePath, JSONObject config) {
        LOG.info(format("Attempting to update API from RAML definition. " +
                "API identifier: %s RAML file: %s", apiId, filePath));

        final Raml raml = parse(filePath);

        client.updateApi(apiId, raml, config);
    }

    @Override
    public void deploy(String apiId, String deploymentStage) {
        client.deploy(apiId, deploymentStage);
    }

    @Override
    public void deleteApi(String apiId) {
        client.deleteApi(apiId);
    }

    private Raml parse(String filePath) {
        final Raml raml = builder.build(filePath);

        // TODO: Error handling.

        return raml;
    }

}
