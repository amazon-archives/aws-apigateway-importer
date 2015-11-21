package com.amazonaws.service.apigateway.importer;

import org.junit.Ignore;
import org.junit.Test;

import java.net.URISyntaxException;
import java.nio.file.Paths;

public class ApiImporterMainTest {

    @Test
    @Ignore
    public void test() throws URISyntaxException {
        ApiImporterMain main = new ApiImporterMain();

        String[] args = {"--create", Paths.get(getClass().getResource("/swagger/apigateway.json").toURI()).toString()};
        main.main(args);
    }

}
