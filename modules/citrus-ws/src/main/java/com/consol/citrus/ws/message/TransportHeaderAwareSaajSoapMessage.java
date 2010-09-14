package com.consol.citrus.ws.message;

import java.io.IOException;
import java.io.OutputStream;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.xml.soap.SOAPMessage;

import org.springframework.ws.soap.saaj.SaajSoapMessage;
import org.springframework.ws.transport.TransportOutputStream;

/**
 * An extension of the SaajSoapMessage which is capable of handling transport
 * header information.
 */
public class TransportHeaderAwareSaajSoapMessage extends SaajSoapMessage implements
        TransportHeaderAwareSoapMessage {
    
    private Map<String, String> transportHeaders = new LinkedHashMap<String, String>();

    /**
     * @see org.springframework.ws.soap.saaj.SaajSoapMessage#SaajSoapMessage(SOAPMessage)
     */
    public TransportHeaderAwareSaajSoapMessage(SOAPMessage soapMessage) {
        super(soapMessage);
    }
    
    /**
     * @see org.springframework.ws.soap.saaj.SaajSoapMessage#SaajSoapMessage(SOAPMessage, boolean)
     */
    public TransportHeaderAwareSaajSoapMessage(SOAPMessage soapMessage, boolean langAttributeOnSoap11FaultString) {
        super(soapMessage, langAttributeOnSoap11FaultString);
    }
    
    /**
     * @see org.springframework.ws.soap.saaj.SaajSoapMessage#writeTo(OutputStream)
     */
    @Override
    public void writeTo(OutputStream outputStream) throws IOException {
        if (outputStream instanceof TransportOutputStream) {
            TransportOutputStream transportOutputStream = (TransportOutputStream)outputStream;
            for (String headerName : transportHeaders.keySet()) {
                transportOutputStream.addHeader(headerName, transportHeaders.get(headerName));
            }
        }
        super.writeTo(outputStream);
    }

    /** Adds a transport header to the message.
     * 
     * @param name the header name
     * @param value the header value
     */
    public void addTransportHeader(String name, String value) {
        if (transportHeaders.containsKey(name)) {
            transportHeaders.put(name, transportHeaders.get(name) + ", " + value);
        } else {
            transportHeaders.put(name, value);
        }
    }

    /** Adds transport headers to the message.
     * 
     * @param headers a map of transport headers
     */
    public void addTransportHeaders(Map<String, String> headers) {
        for (String headerName : headers.keySet()) {
            addTransportHeader(headerName, headers.get(headerName));
        }
    }

    /** Returns the transport headers stored in the message.
     * 
     * @return the transport headers
     */
    public Map<String, String> getTransportHeaders() {
        return transportHeaders;
    }
    
    /**
     * @see org.springframework.ws.soap.saaj.SaajSoapMessage#toString()
     */
    @Override
    public String toString() {
        StringBuffer buffer = new StringBuffer("TransportHeaderAware");
        buffer.append(super.toString());
        return buffer.toString();
    }
}
