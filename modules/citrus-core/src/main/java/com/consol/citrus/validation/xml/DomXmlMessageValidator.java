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

package com.consol.citrus.validation.xml;

import java.io.IOException;
import java.util.*;
import java.util.Map.Entry;

import javax.xml.namespace.NamespaceContext;
import javax.xml.transform.dom.DOMSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.integration.Message;
import org.springframework.integration.MessageHeaders;
import org.springframework.util.*;
import org.springframework.xml.validation.XmlValidator;
import org.springframework.xml.xsd.XsdSchema;
import org.w3c.dom.*;
import org.w3c.dom.ls.LSException;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import com.consol.citrus.CitrusConstants;
import com.consol.citrus.context.TestContext;
import com.consol.citrus.exceptions.*;
import com.consol.citrus.functions.FunctionRegistry;
import com.consol.citrus.functions.FunctionUtils;
import com.consol.citrus.message.MessageType;
import com.consol.citrus.util.XMLUtils;
import com.consol.citrus.validation.AbstractMessageValidator;
import com.consol.citrus.validation.ControlMessageValidator;
import com.consol.citrus.validation.context.ValidationContext;
import com.consol.citrus.variable.VariableUtils;
import com.consol.citrus.xml.XsdSchemaRepository;
import com.consol.citrus.xml.namespace.NamespaceContextBuilder;
import com.consol.citrus.xml.xpath.XPathExpressionResult;
import com.consol.citrus.xml.xpath.XPathUtils;

/**
 * Default message validator implementation. Working on XML messages
 * providing message payload, header and namespace validation.
 *
 * @author Christoph Deppisch
 * @since 2007
 */
public class DomXmlMessageValidator extends AbstractMessageValidator<XmlMessageValidationContext> {
    /**
     * Logger
     */
    private static Logger log = LoggerFactory.getLogger(DomXmlMessageValidator.class);

    @Autowired
    private FunctionRegistry functionRegistry;

    @Autowired
    private XsdSchemaRepository schemaRepository;
    
    @Autowired(required= false)
    private NamespaceContextBuilder namespaceContextBuilder = new NamespaceContextBuilder(); 

    /**
     * Validates the message with test context and xml validation context.
     */
    public void validateMessage(Message<?> receivedMessage, TestContext context, XmlMessageValidationContext validationContext) 
        throws ValidationException {
        log.info("Start XML message validation");
        
        try {
            if (validationContext.isSchemaValidationEnabled()) {
                validateXMLSchema(receivedMessage);
                validateDTD(validationContext.getDTDResource(), receivedMessage);
            }

            validateNamespaces(validationContext.getControlNamespaces(), receivedMessage);
            validateMessagePayload(receivedMessage, validationContext, context);
            validateMessageElements(receivedMessage, validationContext, context);

            Message<?> controlMessage = validationContext.getControlMessage(context);
            if (controlMessage != null) {
                validateMessageHeader(controlMessage.getHeaders(), receivedMessage.getHeaders(), context);
            }
            
            log.info("XML message validation successful: All values OK");
        } catch (ClassCastException e) {
            throw new CitrusRuntimeException(e);
        } catch (DOMException e) {
            throw new CitrusRuntimeException(e);
        } catch (LSException e) {
            throw new CitrusRuntimeException(e);
        } catch (IllegalArgumentException e) {
            log.error("Failed to validate:\n" + XMLUtils.prettyPrint(receivedMessage.getPayload().toString()));
            throw new ValidationException("Validation failed:", e);
        } catch (ValidationException ex) {
            log.error("Failed to validate:\n" + XMLUtils.prettyPrint(receivedMessage.getPayload().toString()));
            throw ex;
        }
    }

    /**
     * Validates the message header comparing a control set of header
     * elements with the actual message header.
     *
     * @param expectedHeaderValues
     * @param receivedHeaderValues
     * @param context
     */
    public void validateMessageHeader(MessageHeaders controlHeaders, 
            MessageHeaders receivedHeaders, 
            TestContext context) {
        ControlMessageValidator validatorDelegate = new ControlMessageValidator();
        validatorDelegate.validateMessageHeader(controlHeaders, receivedHeaders, context);
    }

    /**
     * Validate message payload XML elements.
     *
     * @param receivedMessage
     * @param validationContext
     * @param context
     */
    public void validateMessageElements(Message<?> receivedMessage,
            XmlMessageValidationContext validationContext,
            TestContext context) {
        if (CollectionUtils.isEmpty(validationContext.getPathValidationExpressions())) { return; }

        if (receivedMessage.getPayload() == null || !StringUtils.hasText(receivedMessage.getPayload().toString())) {
            throw new ValidationException("Unable to validate message elements - receive message payload was empty");
        }
        
        log.info("Start XML elements validation");

        Document received = XMLUtils.parseMessagePayload(receivedMessage.getPayload().toString());
        NamespaceContext namespaceContext = namespaceContextBuilder.buildContext(receivedMessage, validationContext.getNamespaces());
        
        for (Entry<String, String> entry : validationContext.getPathValidationExpressions().entrySet()) {
            String elementPathExpression = entry.getKey();
            String expectedValue = entry.getValue();
            String actualValue = null;

            elementPathExpression = context.replaceDynamicContentInString(elementPathExpression);

            if (XPathUtils.isXPathExpression(elementPathExpression)) {
                XPathExpressionResult resultType = XPathExpressionResult.fromString(elementPathExpression, XPathExpressionResult.NODE);
                elementPathExpression = XPathExpressionResult.cutOffPrefix(elementPathExpression);

                //Give ignore elements the chance to prevent the validation in case result type is node
                if(resultType.equals(XPathExpressionResult.NODE) &&
                        isNodeIgnored(XPathUtils.evaluateAsNode(received, 
                                elementPathExpression, 
                                namespaceContext), 
                                validationContext,
                                namespaceContext)) {
                    continue;
                }

                actualValue = XPathUtils.evaluate(received, 
                            elementPathExpression, 
                            namespaceContext, 
                            resultType);
            } else {
                Node node = XMLUtils.findNodeByName(received, elementPathExpression);

                if (node == null) {
                    throw new UnknownElementException("Element ' " + elementPathExpression + "' could not be found in DOM tree");
                }

                if(isNodeIgnored(node, validationContext, namespaceContext)) {
                    continue;
                }

                if (node.getNodeType() == Node.ELEMENT_NODE && node.getFirstChild() != null) {
                    actualValue = node.getFirstChild().getNodeValue();
                } else {
                    actualValue = node.getNodeValue();
                }
            }

            if (VariableUtils.isVariableName(expectedValue)) {
                expectedValue = context.getVariable(expectedValue);
            } else if(functionRegistry.isFunction(expectedValue)) {
                expectedValue = FunctionUtils.resolveFunction(expectedValue, context);
            }

            try {
                if(actualValue != null) {
                    Assert.isTrue(expectedValue != null,
                            "Values not equal for element '"
                                + elementPathExpression + "', expected '"
                                + null + "' but was '"
                                + actualValue + "'");

                    Assert.isTrue(actualValue.equals(expectedValue),
                            "Values not equal for element '"
                                + elementPathExpression + "', expected '"
                                + expectedValue + "' but was '"
                                + actualValue + "'");
                } else {
                    Assert.isTrue(expectedValue == null || expectedValue.length() == 0,
                            "Values not equal for element '"
                                + elementPathExpression + "', expected '"
                                + expectedValue + "' but was '"
                                + null + "'");
                }
            } catch (IllegalArgumentException e) {
                throw new ValidationException("Validation failed:", e);
            }

            if(log.isDebugEnabled()) {
                log.debug("Validating element: " + elementPathExpression + "='" + expectedValue + "': OK.");
            }
        }

        log.info("Validation of XML elements finished successfully: All elements OK");
    }

    /**
     * Validate message with a DTD.
     *
     * @param dtdResource
     * @param receivedMessage
     */
    public void validateDTD(Resource dtdResource, Message<?> receivedMessage) {
        //TODO implement this
    }

    /**
     * Validate message with a XML schema.
     *
     * @param receivedMessage
     */
    public void validateXMLSchema(Message<?> receivedMessage) {
        if (receivedMessage.getPayload() == null || !StringUtils.hasText(receivedMessage.getPayload().toString())) {
            return;
        }
        
        try {
            Document doc = XMLUtils.parseMessagePayload(receivedMessage.getPayload().toString());

            if(!StringUtils.hasText(doc.getFirstChild().getNamespaceURI())) {
                return;
            }

            log.info("Starting XML schema validation ...");

            XsdSchema schema = schemaRepository.getSchemaByNamespace(doc.getFirstChild().getNamespaceURI());

            Assert.notNull(schema, "No schema found in schemaRepository for namespace '" + doc.getFirstChild().getNamespaceURI() + "'");

            XmlValidator validator = schema.createValidator();

            SAXParseException[] results = validator.validate(new DOMSource(doc));

            if(results.length == 0) {
                log.info("Schema of received XML validated OK");
            } else {
                log.error("Schema validation failed for message:\n" + XMLUtils.prettyPrint(receivedMessage.getPayload().toString()));
                throw new ValidationException("Schema validation failed:", results[0]);
            }
        } catch (IOException e) {
            throw new CitrusRuntimeException(e);
        } catch (SAXException e) {
            throw new CitrusRuntimeException(e);
        }
    }

    /**
     * Validate namespaces in message. The method compares namespace declarations in the root
     * element of the received message to expected namespaces. Prefixes are important too, so
     * differing namespace prefixes will fail the validation.
     *
     * @param expectedNamespaces
     * @param receivedMessage
     */
    public void validateNamespaces(Map<String, String> expectedNamespaces, Message<?> receivedMessage) {
        if (CollectionUtils.isEmpty(expectedNamespaces)) { return; }

        if (receivedMessage.getPayload() == null || !StringUtils.hasText(receivedMessage.getPayload().toString())) {
            throw new ValidationException("Unable to validate message namespaces - receive message payload was empty");
        }
        
        log.info("Start XML namespace validation");

        Document received = XMLUtils.parseMessagePayload(receivedMessage.getPayload().toString());

        Map<String, String> foundNamespaces = XMLUtils.lookupNamespaces(receivedMessage.getPayload().toString());

        if (foundNamespaces.size() != expectedNamespaces.size()) {
            throw new ValidationException("Number of namespace declarations not equal for node " + XMLUtils.getNodesPathName(received.getFirstChild()) + " found " + foundNamespaces.size() + " expected " + expectedNamespaces.size());
        }

        for (Entry<String, String> entry : expectedNamespaces.entrySet()) {
            String namespace = entry.getKey();
            String url = entry.getValue();

            if (foundNamespaces.containsKey(namespace)) {
                if (!foundNamespaces.get(namespace).equals(url)) {
                    throw new ValidationException("Namespace '" + namespace + "' values not equal: found '" + foundNamespaces.get(namespace) + "' expected '" + url + "' in reference node " + XMLUtils.getNodesPathName(received.getFirstChild()));
                } else {
                    log.info("Validating namespace " + namespace + " value as expected " + url + " - value OK");
                }
            } else {
                throw new ValidationException("Missing namespace " + namespace + "(" + url + ") in node " + XMLUtils.getNodesPathName(received.getFirstChild()));
            }
        }

        log.info("XML namespace validation finished successfully: All values OK");
    }

    /**
     * Validate message payloads by comparing to a control message.
     *
     * @param receivedMessage
     * @param validationContext
     * @param context
     */
    private void validateMessagePayload(Message<?> receivedMessage, XmlMessageValidationContext validationContext, TestContext context) {
        Message<?> controlMessage = validationContext.getControlMessage(context);
        
        if (controlMessage == null || controlMessage.getPayload() == null) {
            log.info("Skip message payload validation as no control message was defined");
            return;
        }
        
        if (!(controlMessage.getPayload() instanceof String)) {
            throw new IllegalArgumentException("DomXmlMessageValidator does only support message payload of type String, " +
                    "but was " + controlMessage.getPayload().getClass());
        }
        
        String controlMessagePayload = controlMessage.getPayload().toString();

        if (receivedMessage.getPayload() == null || !StringUtils.hasText(receivedMessage.getPayload().toString())) {
            Assert.isTrue(!StringUtils.hasText(controlMessagePayload), 
                    "Unable to validate message payload - received message payload was empty, control message payload is not");
            return;
        } else if (!StringUtils.hasText(controlMessagePayload)) { 
            return; 
        }

        log.info("Start XML tree validation ...");

        Document received = XMLUtils.parseMessagePayload(receivedMessage.getPayload().toString());
        Document source = XMLUtils.parseMessagePayload(controlMessagePayload);

        XMLUtils.stripWhitespaceNodes(received);
        XMLUtils.stripWhitespaceNodes(source);

        if (log.isDebugEnabled()) {
            log.debug("Received message:\n" + XMLUtils.serialize(received));
            log.debug("Control message:\n" + XMLUtils.serialize(source));
        }

        validateXmlTree(received, source, validationContext, namespaceContextBuilder.buildContext(receivedMessage, validationContext.getNamespaces()));
    }

    /**
     * Walk the XML tree and validate all nodes.
     *
     * @param received
     * @param source
     * @param validationContext
     */
    private void validateXmlTree(Node received, Node source, 
            XmlMessageValidationContext validationContext, NamespaceContext namespaceContext) {
        switch(received.getNodeType()) {
            case Node.DOCUMENT_NODE:
                validateXmlTree(received.getFirstChild(), source.getFirstChild(), 
                        validationContext, namespaceContext);
                break;
            case Node.ELEMENT_NODE:
                doElement(received, source, validationContext, namespaceContext);
                break;
            case Node.TEXT_NODE: case Node.CDATA_SECTION_NODE:
                doText(received, source);
                break;
            case Node.ATTRIBUTE_NODE:
                throw new IllegalStateException();
            case Node.COMMENT_NODE:
                doComment(received);
                break;
            case Node.PROCESSING_INSTRUCTION_NODE:
                doPI(received);
                break;
        }
    }

    /**
     * Handle element node.
     *
     * @param received
     * @param source
     * @param validationContext
     */
    private void doElement(Node received, Node source, 
            XmlMessageValidationContext validationContext, NamespaceContext namespaceContext) {
        //validate element name
        if(log.isDebugEnabled()) {
            log.debug("Validating element: " + received.getLocalName() + " (" + received.getNamespaceURI() + ")");
        }

        Assert.isTrue(received.getLocalName().equals(source.getLocalName()),
                "Element names not equal , expected '"
                    + source.getLocalName() + "' but was '"
                    + received.getLocalName() + "'");

        //validate element namespace
        if(log.isDebugEnabled()) {
            log.debug("Validating namespace for element: " + received.getLocalName());
        }

        if(received.getNamespaceURI() != null) {
            Assert.isTrue(source.getNamespaceURI() != null,
                    "Element namespace not equal for element '"
                        + received.getLocalName() + "', expected '"
                        + null + "' but was '"
                        + received.getNamespaceURI() + "'");

            Assert.isTrue(received.getNamespaceURI().equals(source.getNamespaceURI()),
                    "Element namespace not equal for element '"
                        + received.getLocalName() + "', expected '"
                        + source.getNamespaceURI() + "' but was '"
                        + received.getNamespaceURI() + "'");
        } else {
            Assert.isTrue(source.getNamespaceURI() == null,
                    "Element namespace not equal for element '"
                        + received.getLocalName() + "', expected '"
                        + source.getNamespaceURI() + "' but was '"
                        + null + "'");
        }

        //check if element is ignored either by xpath or by ignore placeholder in source message
        if (isNodeIgnored(received, validationContext, namespaceContext)) {
            if(log.isDebugEnabled()) {
                log.debug("Element: '" + received.getLocalName() + "' is on ignore list - skipped validation");
            }
            return;
        } else if(source.getFirstChild() != null &&
                    StringUtils.hasText(source.getFirstChild().getNodeValue()) && 
                    source.getFirstChild().getNodeValue().trim().equals(CitrusConstants.IGNORE_PLACEHOLDER)) {
            if(log.isDebugEnabled()) {
                log.debug("Element: '" + received.getLocalName() + "' is ignored by placeholder '" + CitrusConstants.IGNORE_PLACEHOLDER + "'");
            }
            return;
        }

        //work on attributes
        if(log.isDebugEnabled()) {
            log.debug("Validating attributes for element: " + received.getLocalName());
        }
        NamedNodeMap receivedAttr = received.getAttributes();
        NamedNodeMap sourceAttr = source.getAttributes();

        Assert.isTrue(countAttributes(receivedAttr) == countAttributes(sourceAttr),
                "Number of attributes not equal for element '"
                    + received.getLocalName() + "', expected "
                    + countAttributes(sourceAttr) + " but was "
                    + countAttributes(receivedAttr));

        for(int i = 0; i<receivedAttr.getLength(); i++) {
            doAttribute(received, receivedAttr.item(i), sourceAttr, validationContext, namespaceContext);
        }

        //work on child nodes
        NodeList receivedChilds = received.getChildNodes();
        NodeList sourceChilds = source.getChildNodes();

        Assert.isTrue(receivedChilds.getLength() == sourceChilds.getLength(),
                "Number of child elements not equal for element '"
                    + received.getLocalName() + "', expected "
                    + sourceChilds.getLength() + " but was "
                    + receivedChilds.getLength());

        for(int i = 0; i<receivedChilds.getLength(); i++) {
            this.validateXmlTree(receivedChilds.item(i), sourceChilds.item(i), 
                    validationContext, namespaceContext);
        }

        if(log.isDebugEnabled()) {
            log.debug("Validation successful for element: " + received.getLocalName() + " (" + received.getNamespaceURI() + ")");
        }
    }

    /**
     * Handle text node during validation.
     *
     * @param received
     * @param source
     */
    private void doText(Node received, Node source) {
        if(log.isDebugEnabled()) {
            log.debug("Validating node value for element: " + received.getParentNode());
        }

        if (received.getNodeValue() != null) {
            Assert.isTrue(source.getNodeValue() != null,
                    "Node value not equal for element '"
                            + received.getParentNode().getLocalName() + "', expected '"
                            + null + "' but was '"
                            + received.getNodeValue().trim() + "'");

            Assert.isTrue(received.getNodeValue().trim().equals(source.getNodeValue().trim()),
                    "Node value not equal for element '"
                            + received.getParentNode().getLocalName() + "', expected '"
                            + source.getNodeValue().trim() + "' but was '"
                            + received.getNodeValue().trim() + "'");
        } else {
            Assert.isTrue(source.getNodeValue() == null,
                    "Node value not equal for element '"
                            + received.getParentNode().getLocalName() + "', expected '"
                            + source.getNodeValue().trim() + "' but was '"
                            + null + "'");
        }

        if(log.isDebugEnabled()) {
            log.debug("Node value '" + received.getNodeValue().trim() + "': OK");
        }
    }

    /**
     * Handle attribute node during validation.
     *
     * @param element
     * @param received
     * @param sourceAttributes
     * @param validationContext
     */
    private void doAttribute(Node element, Node received, NamedNodeMap sourceAttributes, 
            XmlMessageValidationContext validationContext, NamespaceContext namespaceContext) {
        if(received.getNodeName().startsWith("xmlns")) { return; }

        String receivedName = received.getLocalName();

        if(log.isDebugEnabled()) {
            log.debug("Validating attribute: " + receivedName + " (" + received.getNamespaceURI() + ")");
        }

        Node source = sourceAttributes.getNamedItemNS(received.getNamespaceURI(), receivedName);

        Assert.isTrue(source != null,
                "Attribute validation failed for element '"
                    + element.getLocalName() + "', unknown attribute "
                    + receivedName + " (" + received.getNamespaceURI() + ")");

        if ((StringUtils.hasText(source.getNodeValue()) && source.getNodeValue().trim().equals(CitrusConstants.IGNORE_PLACEHOLDER)) 
                || isAttributeIgnored(element, received, validationContext, namespaceContext)) {
            if(log.isDebugEnabled()) {
                log.debug("Attribute '" + receivedName + "' is on ignore list - skipped value validation");
            }
            return;
        }

        String receivedValue = received.getNodeValue();
        String sourceValue = source.getNodeValue();

        Assert.isTrue(receivedValue.equals(sourceValue),
                "Values not equal for attribute '"
                    + receivedName + "', expected '"
                    + sourceValue + "' but was '"
                    + receivedValue + "'");

        if(log.isDebugEnabled()) {
            log.debug("Attribute '" + receivedName + "'='" + receivedValue + "': OK");
        }
    }

    /**
     * Handle comment node during validation.
     *
     * @param received
     * @param source
     */
    private void doComment(Node received) {
        log.info("Ignored comment node (" + received.getNodeValue() + ")");
    }

    /**
     * Handle processing instruction during validation.
     *
     * @param received
     * @param source
     */
    private void doPI(Node received) {
        log.info("Ignored processing instruction (" + received.getLocalName() + "=" + received.getNodeValue() + ")");
    }

    /**
     * Counts the attributenode for an element (xmlns attributes ignored)
     * @param attributesR attributesMap
     * @return number of attributes
     */
    private int countAttributes(NamedNodeMap attributesR) {
        int cntAttributes = 0;

        for (int i = 0; i < attributesR.getLength(); i++) {
            if (!attributesR.item(i).getNodeName().startsWith("xmlns")) {
                cntAttributes++;
            }
        }

        return cntAttributes;
    }

    /**
     * Checks whether the current attribute is ignored.
     * @param elementNode
     * @param attributeNode
     * @param validationContext
     * @return
     */
    private boolean isAttributeIgnored(Node elementNode, Node attributeNode, 
            XmlMessageValidationContext validationContext, NamespaceContext namespaceContext) {
        Set<String> ignoreMessageElements = validationContext.getIgnoreExpressions();

        if (CollectionUtils.isEmpty(ignoreMessageElements)) {
            return false;
        }
        
        /** This is the faster version, but then the ignoreValue name must be
         * the full path name like: Numbers.NumberItem.AreaCode
         */
        if (ignoreMessageElements.contains(XMLUtils.getNodesPathName(elementNode) + "." + attributeNode.getNodeName())) {
            return true;
        }

        /** This is the slower version, but here the ignoreValues can be
         * the short path name like only: AreaCode
         *
         * If there are more nodes with the same short name,
         * the first one will match, eg. if there are:
         *      Numbers1.NumberItem.AreaCode
         *      Numbers2.NumberItem.AreaCode
         * And ignoreValues contains just: AreaCode
         * the only first Node: Numbers1.NumberItem.AreaCode will be ignored.
         */
        for (String expression : ignoreMessageElements) {
            Node foundAttributeNode = XMLUtils.findNodeByName(elementNode.getOwnerDocument(), expression);

            if (foundAttributeNode != null && attributeNode.isSameNode(foundAttributeNode)) {
                return true;
            }
        }

        /** This is the XPath version using XPath expressions in
         * ignoreValues to identify nodes to be ignored
         */
        for (String expression : ignoreMessageElements) {
            if (XPathUtils.isXPathExpression(expression)) {
                Node foundAttributeNode = XPathUtils.evaluateAsNode(elementNode.getOwnerDocument(), 
                        expression, 
                        namespaceContext);
                if (foundAttributeNode != null && foundAttributeNode.isSameNode(attributeNode)) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Checks whether the node is ignored.
     * @param node
     * @param validationContext
     * @return
     */
    private boolean isNodeIgnored(final Node node, XmlMessageValidationContext validationContext,
            NamespaceContext namespaceContext) {
        Set<String> ignoreMessageElements = validationContext.getIgnoreExpressions();

        if (CollectionUtils.isEmpty(ignoreMessageElements)) {
            return false;
        }

        /** This is the faster version, but then the ignoreValue name must be
         * the full path name like: Numbers.NumberItem.AreaCode
         */
        if (ignoreMessageElements.contains(XMLUtils.getNodesPathName(node))) {
            return true;
        }

        /** This is the slower version, but here the ignoreValues can be
         * the short path name like only: AreaCode
         *
         * If there are more nodes with the same short name,
         * the first one will match, eg. if there are:
         *      Numbers1.NumberItem.AreaCode
         *      Numbers2.NumberItem.AreaCode
         * And ignoreValues contains just: AreaCode
         * the only first Node: Numbers1.NumberItem.AreaCode will be ignored.
         */
        for (String expression : ignoreMessageElements) {
            if (node == XMLUtils.findNodeByName(node.getOwnerDocument(), expression)) {
                return true;
            }
        }

        /** This is the XPath version using XPath expressions in
         * ignoreValues to identify nodes to be ignored
         */
        for (String expression : ignoreMessageElements) {
            if (XPathUtils.isXPathExpression(expression)) {
                Node foundNode = XPathUtils.evaluateAsNode(node.getOwnerDocument(), 
                            expression, 
                            namespaceContext);

                if (foundNode != null && foundNode.isSameNode(node)) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Returns the needed validation context for this validation mechanism.
     */
    public XmlMessageValidationContext findValidationContext(List<ValidationContext> validationContexts) {
        for (ValidationContext validationContext : validationContexts) {
            if (validationContext instanceof XmlMessageValidationContext) {
                return (XmlMessageValidationContext) validationContext;
            }
        }
        
        return null;
    }

    /**
     * Checks if the message type is supported.
     */
    public boolean supportsMessageType(String messageType) {
        return messageType.equalsIgnoreCase(MessageType.XML.toString());
    }
    
    /**
     * Set the schema repository holding all known schema definition files.
     * @param schemaRepository the schemaRepository to set
     */
    public void setSchemaRepository(XsdSchemaRepository schemaRepository) {
        this.schemaRepository = schemaRepository;
    }

    /**
     * Set the function registry for this validator.
     * @param functionRegistry the functionRegistry to set
     */
    public void setFunctionRegistry(FunctionRegistry functionRegistry) {
        this.functionRegistry = functionRegistry;
    }
}
