package com.amazonaws.service.apigateway.importer.config;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

public class AwsConfig {
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
//        ProfilesConfigFile file = new ProfilesConfigFile(home + "/.aws/config");

        Properties awsConfigProperties = new Properties();

        // todo: use profile
        try {
            awsConfigProperties.load(new FileInputStream(home + "/.aws/config"));
        } catch (IOException e) {
            throw new RuntimeException("Could not load configuration. Please run 'aws configure'");
        }

        region = awsConfigProperties.getProperty("region");
    }

}
