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

package com.consol.citrus.message;

import org.springframework.integration.core.Message;

/**
 * Message receiver interface declares message receiving methods.
 * @author Christoph Deppisch
 */
public interface MessageReceiver {
    /**
     * Receive message.
     * @return the message
     */
    Message<?> receive();
    
    /**
     * Receive message with a given timeout.
     * @param timeout the timeout
     * @return the message
     */
    Message<?> receive(long timeout);
    
    /**
     * Receive message with a given timeout and timeout interval.
     *
     * @param timeout the timeout
     * @param timeoutInterval the timeout interval
     * @return the message
     */
    Message<?> receive(long timeout, long timeoutInterval);
    
    /**
     * Receive message with a message selector string.
     * @param selector the selector
     * @return the message
     */
    Message<?> receiveSelected(String selector);
    
    /**
     * Receive message with a message selector and a receive timeout.
     * @param selector the selector
     * @param timeout the timeout
     * @return
     */
    Message<?> receiveSelected(String selector, long timeout);
    
    /**
     * Receive message with a message selector, a receive timeout and a timeout interval.
     *
     * @param selector the selector
     * @param timeout the timeout
     * @param timeoutInterval the timeout interval
     * @return the message
     */
    Message<?> receiveSelected(String selector, long timeout, long timeoutInterval);
}
