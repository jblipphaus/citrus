/*
 * Copyright 2006-2011 the original author or authors.
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

package com.consol.citrus.validation.json;

import java.util.Iterator;
import java.util.Map.Entry;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.springframework.integration.Message;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import com.consol.citrus.CitrusConstants;
import com.consol.citrus.context.TestContext;
import com.consol.citrus.exceptions.CitrusRuntimeException;
import com.consol.citrus.exceptions.ValidationException;
import com.consol.citrus.message.MessageType;
import com.consol.citrus.validation.ControlMessageValidator;

/**
 * This message validator implementation is able to validate two JSON text objects. The order of JSON entries can differ
 * as specified in JSON protocol. Tester defines an expected control JSON text with optional ignored entries.
 * 
 * JSONArray as well as nested JSONObjects are supported, too.
 * 
 * @author Christoph Deppisch
 */
public class JsonTextMessageValidator extends ControlMessageValidator {

    @Override
    public void validateMessagePayload(Message<?> receivedMessage,
            Message<?> controlMessage,
            TestContext context) throws ValidationException {
        log.info("Start JSON message validation");
        
        if (log.isDebugEnabled()) {
            log.debug("Received message:\n" + receivedMessage);
            log.debug("Control message:\n" + controlMessage);
        }

        String receivedJsonText = receivedMessage.getPayload().toString();
        String controlJsonText = context.replaceDynamicContentInString(controlMessage.getPayload().toString());
        
        try {
            if (!StringUtils.hasText(controlJsonText)) {
                Assert.isTrue(!StringUtils.hasText(receivedJsonText), "Validation failed - " +
                		"expected empty message content, but was: " + receivedJsonText);
                return; // empty message contents as expected - validation finished
            } else {
                Assert.isTrue(StringUtils.hasText(receivedJsonText), "Validation failed - " +
                		"expected message contents, but received empty message!");
            }
            
            JSONParser parser = new JSONParser();
        
        
            JSONObject receivedJson = (JSONObject) parser.parse(receivedJsonText);
            JSONObject controlJson = (JSONObject) parser.parse(controlJsonText);
            
            validateJson(receivedJson, controlJson, context);
        } catch (IllegalArgumentException e) {
            throw new ValidationException("Failed to validate JSON text:\n" + receivedJsonText, e);
        } catch (ParseException e) {
            throw new CitrusRuntimeException("Failed to parse JSON text", e);
        }
        
        log.info("JSON message validation finished successfully: All values OK");
    }
    
    /**
     * Validates JSON text with comparison to expected control JSON object.
     * JSON entries can be ignored with ignore placeholder.
     * 
     * @param receivedJson the received JSON text object.
     * @param controlJson the expected control JSON text.
     * @param context the current test context.
     */
    @SuppressWarnings("rawtypes")
    public void validateJson(JSONObject receivedJson, JSONObject controlJson, TestContext context) {
        Assert.isTrue(controlJson.size() == receivedJson.size(), 
                "Number of JSON entries not equal, expected '" + controlJson.size() + 
                "' but was '" + receivedJson.size() + "'");
        
        for (Iterator it = controlJson.entrySet().iterator(); it.hasNext();) {
            Entry controlJsonEntry = (Entry)it.next();
            
            Assert.isTrue(receivedJson.containsKey(controlJsonEntry.getKey()), 
                    "Missing JSON entry: + '" + controlJsonEntry.getKey() + "'");
            
            if (controlJsonEntry.getValue() == null) {
                Assert.isTrue(receivedJson.get(controlJsonEntry.getKey()) == null, 
                        "Values not equal for entry: '" + controlJsonEntry.getKey() + "'" +
                                ", expected '" + controlJsonEntry.getValue() + "' " +
                                "but was '" + receivedJson.get(controlJsonEntry.getKey()) + "'");
                
                if (log.isDebugEnabled()) {
                    log.debug("Validation successful for JSON entry '" + controlJsonEntry.getKey() + "' (" + controlJsonEntry.getValue() + ")");
                }
                
                continue;
            }
            
            // check if entry is ignored by placeholder
            if (log.isDebugEnabled() && 
                    controlJsonEntry.getValue().toString().trim().equals(CitrusConstants.IGNORE_PLACEHOLDER)) {
                log.debug("JSON entry: '" + controlJsonEntry.getKey() + "' is ignored - skip value validation");
                continue;
            }
            
            if (controlJsonEntry.getValue() instanceof JSONObject) {
                Assert.isTrue(receivedJson.get(controlJsonEntry.getKey()) instanceof JSONObject, 
                        "Type mismatch for JSON entry '" + controlJsonEntry.getKey() + "', " + 
                    "expected '" + JSONObject.class.getSimpleName() + "' but was '" + receivedJson.get(controlJsonEntry.getKey()).getClass().getSimpleName() + "'");
                
                validateJson((JSONObject) receivedJson.get(controlJsonEntry.getKey()), 
                             (JSONObject) controlJsonEntry.getValue(), context);
            } else if (controlJsonEntry.getValue() instanceof JSONArray) {
                Assert.isTrue(receivedJson.get(controlJsonEntry.getKey()) instanceof JSONArray, 
                    "Type mismatch for JSON entry '" + controlJsonEntry.getKey() + "', " + 
                    "expected '" + JSONArray.class.getSimpleName() + "' but was '" + receivedJson.get(controlJsonEntry.getKey()).getClass().getSimpleName() + "'");
                
                JSONArray jsonArrayControl = (JSONArray) controlJsonEntry.getValue();
                JSONArray jsonArrayReceived = (JSONArray) receivedJson.get(controlJsonEntry.getKey());
                
                if (log.isDebugEnabled()) {
                    log.debug("Validating JSONArray containing " + jsonArrayControl.size() + " entries");
                }
                
                Assert.isTrue(jsonArrayControl.size() == jsonArrayReceived.size(), 
                        "JSONArray size mismatch for JSON entry '" + controlJsonEntry.getKey() + "', " +
                        "expected " + jsonArrayControl.size() + " but was " + jsonArrayReceived.size());
                
                for (int i = 0; i < jsonArrayControl.size(); i++) {
                    validateJson((JSONObject) jsonArrayReceived.get(i), 
                            (JSONObject) jsonArrayControl.get(i), context);
                }
            } else {
                Assert.isTrue(controlJsonEntry.getValue().equals(receivedJson.get(controlJsonEntry.getKey())), 
                        "Values not equal for entry: '" + controlJsonEntry.getKey() + "'" +
                                ", expected '" + controlJsonEntry.getValue() + "' " +
                                "but was '" + receivedJson.get(controlJsonEntry.getKey()) + "'");
            }
            
            if (log.isDebugEnabled()) {
                log.debug("Validation successful for JSON entry '" + controlJsonEntry.getKey() + "' (" + controlJsonEntry.getValue() + ")");
            }
        }
    }
    
    @Override
    public boolean supportsMessageType(String messageType) {
        return messageType.equalsIgnoreCase(MessageType.JSON.toString());
    }
}
