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

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.AWSCredentialsProviderChain;
import com.amazonaws.auth.EnvironmentVariableCredentialsProvider;
import com.amazonaws.auth.InstanceProfileCredentialsProvider;
import com.amazonaws.auth.SystemPropertiesCredentialsProvider;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.service.apigateway.importer.config.ApiImporterDefaultModule;
import com.amazonaws.service.apigateway.importer.config.AwsConfig;
import com.amazonaws.service.apigateway.importer.impl.ApiGatewayRamlFileImporter;
import com.amazonaws.service.apigateway.importer.impl.ApiGatewaySwaggerFileImporter;
import com.amazonaws.util.json.JSONException;
import com.amazonaws.util.json.JSONObject;
import com.amazonaws.util.json.JSONTokener;
import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.google.inject.Guice;
import com.google.inject.Injector;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.log4j.ConsoleAppender;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;

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

    @Parameter(names = {"--deploy", "-d"}, description = "Stage used to deploy the API (optional)")
    private String deploymentLabel;

    @Parameter(names = {"--test", "-t"}, description = "Delete the API after import (create only)")
    private boolean cleanup = false;

    @Parameter(names = {"--region", "-r"}, description = "AWS region to use (optional)")
    private String region;

    @Parameter(names = {"--profile", "-p"}, description = "AWS CLI profile to use (optional)")
    private String profile = "default";

    @Parameter(names = {"--raml-config"}, description = "RAML file for API Gateway metadata (optional)")
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

        // use default AWS credentials provider chain
        AWSCredentialsProvider credentialsProvider = new AWSCredentialsProviderChain(
                new EnvironmentVariableCredentialsProvider(),
                new SystemPropertiesCredentialsProvider(),
                new ProfileCredentialsProvider(profile),
                new InstanceProfileCredentialsProvider());

        // if region parameter is not specified, attempt to load configured region from profile
        if (StringUtils.isBlank(region)) {
            AwsConfig config = new AwsConfig(profile);
            try {
                config.load();
            } catch (Throwable t) {
                LOG.error("Could not load AWS configuration. Please run 'aws configure'");
                System.exit(1);
            }
            region = config.getRegion();
        }

        try {
            Injector injector = Guice.createInjector(new ApiImporterDefaultModule(credentialsProvider, region));

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

                importRaml(fileName, configData, importer);
            } else {
                SwaggerApiFileImporter importer = injector.getInstance(ApiGatewaySwaggerFileImporter.class);

                importSwagger(fileName, importer);
            }
        } catch (Throwable t) {
            LOG.error("Error importing API definition", t);
            System.exit(1);
        }
    }

    private void importSwagger(String fileName, SwaggerApiFileImporter importer) {
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

    private void importRaml(String fileName, JSONObject configData, RamlApiFileImporter importer) {
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
