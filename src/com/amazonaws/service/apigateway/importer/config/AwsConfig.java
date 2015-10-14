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
package com.amazonaws.service.apigateway.importer.config;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.Optional;
import java.util.regex.*;

public class AwsConfig {
    private static final Log LOG = LogFactory.getLog(AwsConfig.class);
    public static final String DEFAULT_REGION = "us-east-1";

    private String region;
    private String profile;

    public AwsConfig(String profile) {
        this.profile = profile;
    }

    public String getRegion() {
        return region;
    }

    public String getProfile() {
        return profile;
    }

    public void load() {
        Optional<String> region = loadRegion();

        if (region.isPresent()) {
            this.region = region.get();
        } else {
            this.region = DEFAULT_REGION;
            LOG.warn("Could not load region configuration. Please ensure AWS CLI is " +
                             "configured via 'aws configure'. Will use default region of " + this.region);
        }
    }

    private Optional<String> loadRegion() {
        String file = System.getProperty("user.home") + "/.aws/config";

        boolean foundProfile = false;
        try (BufferedReader br = new BufferedReader(new FileReader(new File(file)))) {
            String line;
            String region;
            Pattern regionPat = Pattern.compile("[a-z]{2}+-[a-z]{2,}+-[0-9]"); 
            Matcher regionMat;
            Integer eqPos;

            while ((line = br.readLine()) != null) {

                if (line.startsWith("[") && line.contains(this.profile)) {
                    foundProfile = true;
                }

                if (foundProfile && line.startsWith("region")) {
                    eqPos = line.indexOf("=");
                    region = line.substring(eqPos + 1, line.length()).trim();
                    regionMat = regionPat.matcher(region);
                    if (! regionMat.matches()) {
                        LOG.error("Region does not match '[a-z]{2}+-[a-z]{2,}+-[0-9]': " + region);
                        throw new RuntimeException("Region does not match '[a-z]{2}+-[a-z]{2,}+-[0-9]'" + region);
                    }
                    return Optional.of(region);
                }
            }
        } catch (Throwable t) {
            throw new RuntimeException("Could not load configuration. Please run 'aws configure'");
        }

        return Optional.empty();
    }

}
