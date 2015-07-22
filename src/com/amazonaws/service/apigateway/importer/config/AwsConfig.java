package com.amazonaws.service.apigateway.importer.config;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.Optional;

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
            while ((line = br.readLine()) != null) {

                if (line.startsWith("[") && line.contains(this.profile)) {
                    foundProfile = true;
                }

                if (foundProfile && line.startsWith("region")) {
                    return Optional.of(line.substring(9, line.length()));
                }
            }
        } catch (Throwable t) {
            throw new RuntimeException("Could not load configuration. Please run 'aws configure'");
        }

        return Optional.empty();
    }

}
