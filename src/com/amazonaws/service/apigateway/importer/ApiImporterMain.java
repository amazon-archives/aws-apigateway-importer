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

import com.amazonaws.service.apigateway.importer.config.ApiImporterModule;
import com.amazonaws.service.apigateway.importer.config.AwsConfig;
import com.amazonaws.service.apigateway.importer.impl.ApiGatewaySwaggerFileImporter;
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

import java.io.File;
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

    @Parameter(names = {"--profile", "-p"}, description = "AWS CLI profile to use")
    private String profile = "default";

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

        AwsConfig config = new AwsConfig(profile);
        try {
            config.load();
        } catch (Throwable t) {
            LOG.error("Could not load AWS configuration. Please run 'aws configure'");
            System.exit(1);
        }

        try {
            Injector injector = Guice.createInjector(new ApiImporterModule(config));

            ApiGatewaySwaggerFileImporter importer = injector.getInstance(ApiGatewaySwaggerFileImporter.class);

            String swaggerFile = files.get(0);

            if (createNew) {
                apiId = importer.importApi(swaggerFile);

                if (cleanup) {
                    importer.deleteApi(apiId);
                }
            } else {
                importer.updateApi(apiId, swaggerFile);
            }

            if (!StringUtils.isBlank(deploymentLabel)) {
                importer.deploy(apiId, deploymentLabel);
            }
        } catch (Throwable t) {
            LOG.error("Error importing API from Swagger", t);
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

        final String swaggerFile = files.get(0);
        if (!new File(swaggerFile).exists()) {
            LOG.error(String.format("Could not load Swagger file '%s'", swaggerFile));
            return false;
        }

        return true;
    }

}
