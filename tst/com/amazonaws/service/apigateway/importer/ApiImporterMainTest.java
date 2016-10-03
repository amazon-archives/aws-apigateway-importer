package com.amazonaws.service.apigateway.importer;

import org.junit.Ignore;
import org.junit.Test;

import java.net.URISyntaxException;
import java.nio.file.Paths;

public class ApiImporterMainTest {

    @Test
    public void test() throws URISyntaxException {
        ApiImporterMain main = new ApiImporterMain();

        String[] args = {"--create", Paths.get(getClass().getResource("/swagger/ube.json").toURI()).toString()};
        main.main(args);
    }

}
