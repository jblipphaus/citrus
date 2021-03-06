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

package com.consol.citrus.adapter.common.endpoint;

import org.springframework.integration.Message;

import com.consol.citrus.exceptions.CitrusRuntimeException;
import com.consol.citrus.message.CitrusMessageHeaders;

/**
 * Endpoint uri resolver working on message headers. Resolver is searching for a specific header entry which holds the actual
 * target endpoint uri.
 * 
 * @author Christoph Deppisch
 */
public class MessageHeaderEndpointUriResolver implements EndpointUriResolver {

    /** Static header entry name specifying the dynamic endpoint uri */
    public static final String ENDPOINT_URI_HEADER_NAME = CitrusMessageHeaders.PREFIX + "endpoint_uri";
    
    /** Default fallback uri */
    private String defaultEndpointUri;
    
    /**
     * Get the endpoint uri according to message header entry.
     */
    public String resolveEndpointUri(Message<?> message) {
        if (message.getHeaders().containsKey(ENDPOINT_URI_HEADER_NAME)) {
            return message.getHeaders().get(ENDPOINT_URI_HEADER_NAME).toString();
        }
        
        if (defaultEndpointUri != null) {
            return defaultEndpointUri;
        } else {
            throw new CitrusRuntimeException("Unable to resolve dynamic endpoint uri for this message - missing header entry '" + 
                    ENDPOINT_URI_HEADER_NAME + "' specifying the endpoint uri neither default endpoint uri is set");
        }
    }
    
    /**
     * Get the endpoint uri according to message header entry with fallback default uri.
     */
    public String resolveEndpointUri(Message<?> message, String defaultUri) {
        if (message.getHeaders().containsKey(ENDPOINT_URI_HEADER_NAME)) {
            return message.getHeaders().get(ENDPOINT_URI_HEADER_NAME).toString();
        }
        
        return defaultUri;
    }

    /**
     * Sets the default fallback endpoint uri.
     * @param defaultEndpointUri the defaultUri to set
     */
    public void setDefaultEndpointUri(String defaultEndpointUri) {
        this.defaultEndpointUri = defaultEndpointUri;
    }
}
