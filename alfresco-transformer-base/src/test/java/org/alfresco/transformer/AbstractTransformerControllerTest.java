/*
 * #%L
 * Alfresco Repository
 * %%
 * Copyright (C) 2005 - 2018 Alfresco Software Limited
 * %%
 * This file is part of the Alfresco software.
 * If the software was purchased under a paid Alfresco license, the terms of
 * the paid license agreement will prevail.  Otherwise, the software is
 * provided under the following open source license terms:
 *
 * Alfresco is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Alfresco is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Alfresco. If not, see <http://www.gnu.org/licenses/>.
 * #L%
 */
package org.alfresco.transformer;

import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.nio.channels.FileChannel;
import java.nio.file.Files;

import org.alfresco.transform.client.model.TransformReply;
import org.alfresco.transform.client.model.TransformRequest;
import org.alfresco.transformer.clients.AlfrescoSharedFileStoreClient;
import org.alfresco.transformer.probes.ProbeTestTransform;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Super class for testing controllers without a server. Includes tests for the AbstractTransformerController itself.
 */
public abstract class AbstractTransformerControllerTest
{
    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @MockBean
    protected AlfrescoSharedFileStoreClient alfrescoSharedFileStoreClient;

    protected String sourceExtension;
    protected String targetExtension;
    protected String sourceMimetype;

    protected MockMultipartFile sourceFile;
    protected String expectedOptions;
    protected String expectedSourceSuffix;
    protected Long expectedTimeout = 0L;
    protected byte[] expectedSourceFileBytes;
    protected byte[] expectedTargetFileBytes;

    // Called by sub class
    protected abstract void mockTransformCommand(String sourceExtension,
        String targetExtension, String sourceMimetype,
        boolean readTargetFileBytes) throws IOException;

    protected abstract AbstractTransformerController getController();

    protected abstract void updateTransformRequestWithSpecificOptions(TransformRequest transformRequest);

    /**
     * This method ends up being the core of the mock.
     * It copies content from an existing file in the resources folder to the desired location
     * in order to simulate a successful transformation.
     *
     * @param actualTargetExtension Requested extension.
     * @param testFile              The test file (transformed) - basically the result.
     * @param targetFile            The location where the content from the testFile should be copied
     * @throws IOException in case of any errors.
     */
    void generateTargetFileFromResourceFile(String actualTargetExtension, File testFile,
        File targetFile) throws IOException
    {
        if (testFile != null)
        {
            FileChannel source = new FileInputStream(testFile).getChannel();
            FileChannel target = new FileOutputStream(targetFile).getChannel();
            target.transferFrom(source, 0, source.size());
        }
        else
        {
            testFile = getTestFile("quick." + actualTargetExtension, false);
            if (testFile != null)
            {
                FileChannel source = new FileInputStream(testFile).getChannel();
                FileChannel target = new FileOutputStream(targetFile).getChannel();
                target.transferFrom(source, 0, source.size());
            }
        }
    }

    protected byte[] readTestFile(String extension) throws IOException
    {
        return Files.readAllBytes(getTestFile("quick." + extension, true).toPath());
    }

    protected File getTestFile(String testFilename, boolean required) throws IOException
    {
        ClassLoader classLoader = getClass().getClassLoader();
        URL testFileUrl = classLoader.getResource(testFilename);
        if (required && testFileUrl == null)
        {
            throw new IOException("The test file " + testFilename + " does not exist in the resources directory");
        }
        return testFileUrl == null ? null : new File(testFileUrl.getFile());
    }

    protected MockHttpServletRequestBuilder mockMvcRequest(String url, MockMultipartFile sourceFile, String... params)
    {
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.multipart("/transform").file(sourceFile);

        if (params.length % 2 != 0)
        {
            throw new IllegalArgumentException("each param should have a name and value.");
        }
        for (int i = 0; i < params.length; i += 2)
        {
            builder = builder.param(params[i], params[i + 1]);
        }

        return builder;
    }

    @Test
    public void simpleTransformTest() throws Exception
    {
        mockMvc.perform(mockMvcRequest("/transform", sourceFile, "targetExtension", targetExtension))
               .andExpect(status().is(200))
               .andExpect(content().bytes(expectedTargetFileBytes))
               .andExpect(header().string("Content-Disposition", "attachment; filename*= UTF-8''quick." + targetExtension));
    }

    @Test
    public void testDelayTest() throws Exception
    {
        long start = System.currentTimeMillis();
        mockMvc.perform(mockMvcRequest("/transform", sourceFile, "targetExtension", targetExtension, "testDelay", "400"))
               .andExpect(status().is(200))
               .andExpect(content().bytes(expectedTargetFileBytes))
               .andExpect(header().string("Content-Disposition", "attachment; filename*= UTF-8''quick." + targetExtension));
        long ms = System.currentTimeMillis() - start;
        System.out.println("Transform incluing test delay was " + ms);
        assertTrue("Delay sending the result back was too small " + ms, ms >= 400);
        assertTrue("Delay sending the result back was too big " + ms, ms <= 500);
    }

    @Test
    public void noTargetFileTest() throws Exception
    {
        mockMvc.perform(mockMvcRequest("/transform", sourceFile, "targetExtension", "xxx"))
               .andExpect(status().is(500));
    }

    @Test
    // Looks dangerous but is okay as we only use the final filename
    public void dotDotSourceFilenameTest() throws Exception
    {
        sourceFile = new MockMultipartFile("file", "../quick." + sourceExtension, sourceMimetype, expectedSourceFileBytes);

        mockMvc.perform(mockMvcRequest("/transform", sourceFile, "targetExtension", targetExtension))
               .andExpect(status().is(200))
               .andExpect(content().bytes(expectedTargetFileBytes))
               .andExpect(header().string("Content-Disposition", "attachment; filename*= UTF-8''quick." + targetExtension));
    }

    @Test
    // Is okay, as the target filename is built up from the whole source filename and the targetExtenstion
    public void noExtensionSourceFilenameTest() throws Exception
    {
        sourceFile = new MockMultipartFile("file", "../quick", sourceMimetype, expectedSourceFileBytes);

        mockMvc.perform(mockMvcRequest("/transform", sourceFile, "targetExtension", targetExtension))
               .andExpect(status().is(200))
               .andExpect(content().bytes(expectedTargetFileBytes))
               .andExpect(header().string("Content-Disposition", "attachment; filename*= UTF-8''quick." + targetExtension));
    }

    @Test
    // Invalid file name that ends in /
    public void badSourceFilenameTest() throws Exception
    {
        sourceFile = new MockMultipartFile("file", "abc/", sourceMimetype, expectedSourceFileBytes);

        mockMvc.perform(mockMvcRequest("/transform", sourceFile, "targetExtension", targetExtension))
               .andExpect(status().is(400))
               .andExpect(status().reason(containsString("The source filename was not supplied")));
    }

    @Test
    public void blankSourceFilenameTest() throws Exception
    {
        sourceFile = new MockMultipartFile("file", "", sourceMimetype, expectedSourceFileBytes);

        mockMvc.perform(mockMvcRequest("/transform", sourceFile, "targetExtension", targetExtension))
               .andExpect(status().is(400))
               .andExpect(status().reason(containsString("The source filename was not supplied")));
    }

    @Test
    public void noTargetExtensionTest() throws Exception
    {
        mockMvc.perform(mockMvcRequest("/transform", sourceFile))
               .andExpect(status().is(400))
               .andExpect(status().reason(containsString("Request parameter targetExtension is missing")));
    }

    @Test
    public void calculateMaxTime() throws Exception
    {
        ProbeTestTransform probeTestTransform = getController().getProbeTestTransform();
        probeTestTransform.setLivenessPercent(110);

        long[][] values = new long[][]{
            {5000, 0, Long.MAX_VALUE}, // 1st transform is ignored
            {1000, 1000, 2100},        // 1000 + 1000*1.1
            {3000, 2000, 4200},        // 2000 + 2000*1.1
            {2000, 2000, 4200},
            {6000, 3000, 6300},
            {8000, 4000, 8400},
            {4444, 4000, 8400},        // no longer in the first few, so normal and max times don't change
            {5555, 4000, 8400}
        };

        for (long[] v : values)
        {
            long time = v[0];
            long expectedNormalTime = v[1];
            long expectedMaxTime = v[2];

            probeTestTransform.calculateMaxTime(time, true);
            assertEquals("", expectedNormalTime, probeTestTransform.getNormalTime());
            assertEquals("", expectedMaxTime, probeTestTransform.getMaxTime());
        }
    }

    @Test
    public void testEmptyPojoTransform() throws Exception
    {
        // Transformation Request POJO
        TransformRequest transformRequest = new TransformRequest();

        // Serialize and call the transformer
        String tr = objectMapper.writeValueAsString(transformRequest);
        String transformationReplyAsString = mockMvc
            .perform(MockMvcRequestBuilders
                .post("/transform")
                .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .content(tr))
            .andExpect(status().is(HttpStatus.BAD_REQUEST.value()))
            .andReturn().getResponse().getContentAsString();

        TransformReply transformReply = objectMapper.readValue(transformationReplyAsString, TransformReply.class);

        // Assert the reply
        assertEquals(HttpStatus.BAD_REQUEST.value(), transformReply.getStatus());
    }
}
