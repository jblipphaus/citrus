package com.consol.citrus.ws;

import org.testng.ITestContext;
import org.testng.annotations.Test;

import com.consol.citrus.testng.AbstractTestNGCitrusTest;

/**
 * 
 * @author deppisch Christoph Deppisch Consol* Software GmbH
 * @since 31.10.2008
 */
public class WebServiceServerTest extends AbstractTestNGCitrusTest {
    @Test
    public void webServiceServerTest(ITestContext testContext) {
        executeTest(testContext);
    }
}