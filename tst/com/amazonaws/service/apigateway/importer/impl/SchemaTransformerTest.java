package com.amazonaws.service.apigateway.importer.impl;

import junit.framework.Assert;
import junit.framework.TestCase;
import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Paths;
import java.util.ArrayList;

/**
 * Created by srinikandula on 10/2/16.
 */
public class SchemaTransformerTest {
    @Before
    public void setUp() throws Exception {


    }
    @After
    public void tearDown() throws Exception {

    }
    private String readFile(String filePath) throws URISyntaxException, IOException {
        return FileUtils.readFileToString(new File(Paths.get(getClass().getResource(filePath).toURI()).toString()), "UTF-8");
    }
    @Test
    public void testFlattenPlaylist() throws Exception {
        String playList = readFile("/swagger/ube_playlist.json");
        String models = readFile("/swagger/ube_models.json");
        SchemaTransformer transformer = new SchemaTransformer();
        String playListJson = transformer.flatten(playList, models, new ArrayList<>());
        Assert.assertNotNull(playListJson);
    }

    public void testFlatten1() throws Exception {

    }

    public void testDeserialize() throws Exception {

    }

    public void testGetSchemaName() throws Exception {

    }

    public void testGetRestApiId() throws Exception {

    }
}