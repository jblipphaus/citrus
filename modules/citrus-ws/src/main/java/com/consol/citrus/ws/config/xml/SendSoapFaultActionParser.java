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

package com.consol.citrus.ws.config.xml;

import java.util.*;

import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.xml.BeanDefinitionParser;
import org.springframework.beans.factory.xml.ParserContext;
import org.springframework.util.StringUtils;
import org.springframework.util.xml.DomUtils;
import org.w3c.dom.Element;

import com.consol.citrus.config.xml.DescriptionElementParser;
import com.consol.citrus.util.FileUtils;
import com.consol.citrus.ws.message.builder.SoapFaultAwareMessageBuilder;

/**
 * Bean definition parser for send soap fault action in test case.
 * 
 * @author Christoph Deppisch
 * @since 2010
 */
public class SendSoapFaultActionParser implements BeanDefinitionParser {

    /**
     * @see org.springframework.beans.factory.xml.BeanDefinitionParser#parse(org.w3c.dom.Element, org.springframework.beans.factory.xml.ParserContext)
     */
    public BeanDefinition parse(Element element, ParserContext parserContext) {
        String messageSenderReference = element.getAttribute("with");
        
        if (!StringUtils.hasText(messageSenderReference)) {
            throw new BeanCreationException("Mandatory 'with' attribute has to be set!");
        }

        BeanDefinitionBuilder builder = BeanDefinitionBuilder.genericBeanDefinition("com.consol.citrus.actions.SendMessageAction");
        builder.addPropertyValue("name", element.getLocalName());

        builder.addPropertyReference("messageSender", messageSenderReference);
        
        DescriptionElementParser.doParse(element, builder);

        SoapFaultAwareMessageBuilder messageBuilder = new SoapFaultAwareMessageBuilder();
        
        Element faultElement = DomUtils.getChildElementByTagName(element, "fault");
        if (faultElement != null) {
            Element faultCodeElement = DomUtils.getChildElementByTagName(faultElement, "fault-code");
            if (faultCodeElement != null) {
                messageBuilder.setFaultCode(DomUtils.getTextValue(faultCodeElement).trim());
            }
            
            Element faultStringElement = DomUtils.getChildElementByTagName(faultElement, "fault-string");
            if (faultStringElement != null) {
                messageBuilder.setFaultString(DomUtils.getTextValue(faultStringElement).trim());
            }
            
            Element faultDetailElement = DomUtils.getChildElementByTagName(faultElement, "fault-detail");
            if (faultDetailElement != null) {
                if(faultDetailElement.hasAttribute("file")) {
                    
                    if(StringUtils.hasText(DomUtils.getTextValue(faultDetailElement).trim())) {
                        throw new BeanCreationException("You tried to set fault-detail by file resource attribute and inline text value at the same time! " +
                        		"Please choose one of them.");
                    }
                    
                    String filePath = faultDetailElement.getAttribute("file");
                    
                    messageBuilder.setFaultDetailResource(FileUtils.getResourceFromFilePath(filePath));
                } else {
                    String faultDetailData = DomUtils.getTextValue(faultDetailElement).trim();
                    if(StringUtils.hasText(faultDetailData)) {
                        messageBuilder.setFaultDetail(faultDetailData);
                    } else {
                        throw new BeanCreationException("Not content for fault-detail is set! Either use file attribute or inline text value for fault-detail element.");
                    }
                }
            }
        }

        Element headerElement = DomUtils.getChildElementByTagName(element, "header");
        Map<String, Object> headerValues = new HashMap<String, Object>();
        if (headerElement != null) {
            List<?> elements = DomUtils.getChildElementsByTagName(headerElement, "element");
            for (Iterator<?> iter = elements.iterator(); iter.hasNext();) {
                Element headerValue = (Element) iter.next();
                headerValues.put(headerValue.getAttribute("name"), headerValue.getAttribute("value"));
            }
            messageBuilder.setMessageHeaders(headerValues);
        }
        
        builder.addPropertyValue("messageBuilder", messageBuilder);
        
        return builder.getBeanDefinition();
    }
}
