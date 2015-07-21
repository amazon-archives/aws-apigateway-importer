package com.amazonaws.service.apigateway.importer.config;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

public class AwsConfig {
    private static final Log LOG = LogFactory.getLog(AwsConfig.class);

    private String region = "us-east-1";
    private String profile = "default";

    public String getRegion() {
        return region;
    }

    public String getProfile() {
        return profile;
    }

    public void load() {
        String home = System.getProperty("user.home");

        Properties awsConfigProperties = new Properties();

        try {
            // todo: use default profile
            awsConfigProperties.load(new FileInputStream(home + "/.aws/config"));
        } catch (IOException e) {
            throw new RuntimeException("Could not load configuration. Please run 'aws configure'");
        }

        String region = awsConfigProperties.getProperty("region");

        if (region != null) {
            this.region = region;
        } else {
            LOG.warn("Could not load region configuration. Please ensure AWS CLI is configured via 'aws configure'. Will use default region of " + this.region);
        }

    }

}
