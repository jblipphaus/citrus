/*
 * Copyright 2006-2010 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.consol.citrus.config.xml;

import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.BeanDefinitionStoreException;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.consol.citrus.actions.EchoAction;
import com.consol.citrus.testng.AbstractBeanDefinitionParserBaseTest;

/**
 * @author Christoph Deppisch
 */
public class ActionParserTest extends AbstractBeanDefinitionParserBaseTest {

    @Test
    public void testActionParser() {
        Assert.assertEquals(getTestCase().getActions().size(), 1);
        Assert.assertEquals(getTestCase().getActions().get(0).getClass(), EchoAction.class);
        
        Assert.assertEquals(getTestCase().getActions().get(0).getName(), "action:echoAction");
    }
    
    @Test
    public void testActionParserBrokenReference() {
        try {
            createApplicationContext("failed");
            Assert.fail("Missing bean creation exception due to broken bean reference");
        } catch (BeanCreationException e) {
            Assert.assertTrue(e.getMessage().contains("NoSuchBeanDefinitionException: No bean named 'brokenRef' is defined"));
        }
    }
    
    @Test
    public void testActionParserMissingReference() {
        try {
            createApplicationContext("missing-ref");
            Assert.fail("Missing bean creation exception due to missing bean reference");
        } catch (BeanDefinitionStoreException e) {
            Assert.assertEquals(e.getCause().getMessage(), "Must specify proper reference attribute to bean");
        }
    }
}
