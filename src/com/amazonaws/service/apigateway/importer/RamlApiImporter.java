package com.amazonaws.service.apigateway.importer;

import com.amazonaws.util.json.JSONObject;
import org.raml.model.Raml;

public interface RamlApiImporter {
    String createApi(Raml raml, String name, JSONObject config);
    void updateApi(String apiId, Raml raml, JSONObject config);
    void deploy(String apiId, String deploymentStage);
    void deleteApi(String apiId);
}
