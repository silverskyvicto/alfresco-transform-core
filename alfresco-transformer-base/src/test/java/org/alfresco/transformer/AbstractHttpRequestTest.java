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

import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.embedded.LocalServerPort;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.util.LinkedMultiValueMap;

import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.springframework.test.util.AssertionErrors.assertTrue;

/**
 * Super class for testing controllers with a server. Includes tests for the AbstractTransformerController itself.
 * Note: Currently uses json rather than HTML as json is returned by this spring boot test harness.
 */
public abstract class AbstractHttpRequestTest
{
    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    protected abstract String getTransformerName();

    protected abstract String getSourceExtension();

    @Test
    public void testPageExists() throws Exception
    {
        String result = restTemplate.getForObject("http://localhost:" + port + "/", String.class);

        String title = getTransformerName() + ' ' + "Test Transformation";
        assertTrue("\"" + title + "\" should be part of the page title", result.contains(title));
    }

    @Test
    public void logPageExists() throws Exception
    {
        String result = restTemplate.getForObject("http://localhost:" + port + "/log", String.class);

        String title = getTransformerName() + ' ' + "Log";
        assertTrue("\"" + title + "\" should be part of the page title", result.contains(title));
    }

    @Test
    public void errorPageExists() throws Exception
    {
        String result = restTemplate.getForObject("http://localhost:" + port + "/error", String.class);

        String title = getTransformerName() + ' ' + "Error Page";
        assertTrue("\"" + title + "\" should be part of the page title", result.contains("Error Page"));
    }

    @Test
    public void noFileError() throws Exception
    {
        // Transformer name is not part of the title as this is checked by another handler
        assertTransformError(false,
                "Required request part 'file' is not present");
    }

    @Test
    public void noTargetExtensionError() throws Exception
    {
        assertMissingParameter("targetExtension");
    }

    protected void assertMissingParameter(String name) throws IOException
    {
        assertTransformError(true,
                getTransformerName() + " - Request parameter " + name + " is missing");
    }

    protected void assertTransformError(boolean addFile, String errorMessage) throws IOException
    {
        LinkedMultiValueMap<String, Object> parameters = new LinkedMultiValueMap<String, Object>();
        if (addFile)
        {
            parameters.add("file", new org.springframework.core.io.ClassPathResource("quick."+getSourceExtension()));
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        HttpEntity<LinkedMultiValueMap<String, Object>> entity = new HttpEntity<LinkedMultiValueMap<String, Object>>(parameters, headers);
        ResponseEntity<String> response = restTemplate.exchange("/transform", HttpMethod.POST, entity, String.class, "");
        assertEquals(errorMessage, getErrorMessage(response.getBody()));
    }

    // Strip out just the error message from the returned json content body
    // Had been expecting the Error page to be returned, but we end up with the json in this test harness.
    // Is correct if run manually, so not worrying too much about this.
    private String getErrorMessage(String content) throws IOException
    {
        String message = "";
        int i = content.indexOf("\"message\":\"");
        if (i != -1)
        {
            int j = content.indexOf("\",\"path\":", i);
            if (j != -1)
            {
                message = content.substring(i+11, j);
            }
        }
        return message;
    }
}