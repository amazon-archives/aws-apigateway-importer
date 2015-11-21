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
package com.amazonaws.service.apigateway.importer;

import com.amazonaws.util.json.JSONObject;

public interface RamlApiFileImporter {
    String importApi(String filePath, JSONObject config);
    void updateApi(String apiId, String filePath, JSONObject config);
    void deploy(String apiId, String deploymentStage);
    void deleteApi(String apiId);
}
