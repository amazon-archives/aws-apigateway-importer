package com.amazonaws.service.apigateway.importer.integration;

import com.amazonaws.service.apigateway.importer.ApiImporterMain;
import org.junit.Ignore;
import org.junit.Test;

import java.net.URISyntaxException;
import java.nio.file.Paths;

public class LargeApiIntegrationTest {

    @Test
    @Ignore // todo: integrate into CI
    public void test() throws URISyntaxException {
        ApiImporterMain main = new ApiImporterMain();

        String[] args = {"--create", Paths.get(getClass().getResource("/swagger/large.json").toURI()).toString()};
        main.main(args);
    }

}
