package com.consol.citrus.ws.message;

import java.util.Map;

import org.springframework.ws.soap.SoapMessage;

/**
 * The Interface TransportHeaderAwareSoapMessage extends SoapMessage to be able
 * to handle transport header information.
 */
public interface TransportHeaderAwareSoapMessage extends SoapMessage {
    void addTransportHeader(String name, String value);
    void addTransportHeaders(Map<String, String> headers);
    Map<String, String> getTransportHeaders();
}
