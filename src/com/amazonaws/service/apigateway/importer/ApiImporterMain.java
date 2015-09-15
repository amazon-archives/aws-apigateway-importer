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

import com.amazonaws.auth.*;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;

import com.amazonaws.service.apigateway.importer.config.ApiImporterModule;
import com.amazonaws.service.apigateway.importer.impl.ApiGatewaySwaggerFileImporter;
import com.amazonaws.service.apigateway.importer.impl.ApiGatewayRamlFileImporter;
import com.amazonaws.util.json.JSONException;
import com.amazonaws.util.json.JSONObject;
import com.amazonaws.util.json.JSONTokener;
import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.google.inject.Guice;
import com.google.inject.Injector;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.log4j.ConsoleAppender;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;
import org.apache.commons.io.FilenameUtils;

import java.io.File;
import java.io.FileReader;
import java.util.List;

public class ApiImporterMain {
    private static final Log LOG = LogFactory.getLog(ApiImporterMain.class);
    private static final String CMD_NAME = "aws-api-import";

    @Parameter(names = {"--update", "-u"}, description = "API ID to import swagger into an existing API")
    private String apiId;

    @Parameter(names = {"--create", "-c"}, description = "Create a new API")
    private boolean createNew;

    @Parameter(description = "Path to API definition file to import")
    private List<String> files;

    @Parameter(names = {"--deploy", "-d"}, description = "Stage used to deploy the API")
    private String deploymentLabel;

    @Parameter(names = {"--test", "-t"}, description = "Delete the API after import (create only)")
    private boolean cleanup = false;

    @Parameter(names = {"--region", "-r"}, description = "AWS region to use")
    private String region = "us-east-1";

    @Parameter(names = {"--profile", "-p"}, description = "AWS CLI profile to use")
    private String profile = "default";

    @Parameter(names = {"--raml-config"}, description = "Provide a configuration file to load AWS information from")
    private String configFile;

    @Parameter(names = "--help", help = true)
    private boolean help;

    public static void main(String[] args) {
        bootstrap();
        ApiImporterMain main = new ApiImporterMain();
        JCommander jCommander = new JCommander(main, args);
        jCommander.setProgramName(CMD_NAME);
        main.execute(jCommander);
    }

    static void bootstrap() {
        Logger root = Logger.getRootLogger();
        root.setLevel(Level.INFO);
        root.addAppender(new ConsoleAppender(new PatternLayout("%d %p - %m%n")));
    }

    public void execute(JCommander jCommander) {
        if (help) {
            jCommander.usage();
            return;
        }

        if (!validateArgs()) {
            jCommander.usage();
            System.exit(1);
        }

        AWSCredentialsProvider credentialsProvider;
        credentialsProvider = new AWSCredentialsProviderChain(
                new EnvironmentVariableCredentialsProvider(),
                new SystemPropertiesCredentialsProvider(),
                new ProfileCredentialsProvider(profile),
                new InstanceProfileCredentialsProvider());

        try {
            Injector injector = Guice.createInjector(new ApiImporterModule(credentialsProvider, region));

            String fileName = files.get(0);

            if (FilenameUtils.getExtension(fileName).equals("raml")) {
                final JSONObject configData;

                RamlApiFileImporter importer = injector.getInstance(ApiGatewayRamlFileImporter.class);

                try {
                    configData = configFile == null ? null : new JSONObject(new JSONTokener(new FileReader(configFile)));
                } catch (JSONException e) {
                    LOG.info("Unable to parse configuration file: " + e);
                    System.exit(1);
                    return;
                }

                if (createNew) {
                    apiId = importer.importApi(fileName, configData);

                    if (cleanup) {
                        importer.deleteApi(apiId);
                    }
                } else {
                    importer.updateApi(apiId, fileName, configData);
                }

                if (!StringUtils.isBlank(deploymentLabel)) {
                    importer.deploy(apiId, deploymentLabel);
                }
            } else {
                SwaggerApiFileImporter importer = injector.getInstance(ApiGatewaySwaggerFileImporter.class);

                if (createNew) {
                    apiId = importer.importApi(fileName);

                    if (cleanup) {
                        importer.deleteApi(apiId);
                    }
                } else {
                    importer.updateApi(apiId, fileName);
                }

                if (!StringUtils.isBlank(deploymentLabel)) {
                    importer.deploy(apiId, deploymentLabel);
                }
            }
        } catch (Throwable t) {
            LOG.error("Error importing API definition", t);
            System.exit(1);
        }
    }

    private boolean validateArgs() {
        if ((apiId == null && !createNew) || files == null || files.isEmpty()) {
            return false;
        }

        if (cleanup && apiId != null) {
            LOG.error("Test mode is not supported when updating an API");
            return false;
        }

        final String fileName = files.get(0);
        
        if (!new File(fileName).exists()) {
            LOG.error(String.format("Could not load file '%s'", fileName));
            return false;
        }

        return true;
    }

}
