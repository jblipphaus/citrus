package com.consol.citrus.ws.message;

import java.io.IOException;
import java.io.OutputStream;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.axiom.attachments.Attachments;
import org.apache.axiom.soap.SOAPFactory;
import org.apache.axiom.soap.SOAPMessage;
import org.springframework.ws.soap.axiom.AxiomSoapMessage;
import org.springframework.ws.transport.TransportOutputStream;

/**
 * An extension of the AxiomSoapMessage which is capable of handling transport
 * header information.
 */
public class TransportHeaderAwareAxiomSoapMessage extends AxiomSoapMessage
		implements TransportHeaderAwareSoapMessage {
	
	private Map<String, String> transportHeaders = new LinkedHashMap<String, String>();

	/**
	 * @see org.springframework.ws.soap.axiom.AxiomSoapMessage#AxiomSoapMessage(SOAPFactory)
	 */
	public TransportHeaderAwareAxiomSoapMessage(SOAPFactory soapFactory) {
		super(soapFactory);
	}
	
	/**
	 * @see org.springframework.ws.soap.axiom.AxiomSoapMessage#AxiomSoapMessage(SOAPMessage, boolean, boolean)
	 */
	public TransportHeaderAwareAxiomSoapMessage(SOAPFactory soapFactory, boolean payloadCaching, boolean langAttributeOnSoap11FaultString) {
        super(soapFactory, payloadCaching, langAttributeOnSoap11FaultString);
    }
	
	/**
	 * @see org.springframework.ws.soap.axiom.AxiomSoapMessage#AxiomSoapMessage(SOAPMessage, String, boolean, boolean)
	 */
	public TransportHeaderAwareAxiomSoapMessage(SOAPMessage soapMessage,
            String soapAction,
            boolean payloadCaching,
            boolean langAttributeOnSoap11FaultString) {
		super(soapMessage, soapAction, payloadCaching, langAttributeOnSoap11FaultString);
	}
	
	/**
	 * @see org.springframework.ws.soap.axiom.AxiomSoapMessage#AxiomSoapMessage(SOAPMessage, Attachments, String, boolean, boolean)
	 */
	public TransportHeaderAwareAxiomSoapMessage(SOAPMessage soapMessage,
            Attachments attachments,
            String soapAction,
            boolean payloadCaching,
            boolean langAttributeOnSoap11FaultString) {
		super(soapMessage, attachments, soapAction, payloadCaching, langAttributeOnSoap11FaultString);
	}
	
	/**
	 * @see org.springframework.ws.soap.axiom.AxiomSoapMessage#writeTo(OutputStream)
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
	 * @see org.springframework.ws.soap.axiom.AxiomSoapMessage#toString()
	 */
    @Override
    public String toString() {
        StringBuffer buffer = new StringBuffer("TransportHeaderAware");
        buffer.append(super.toString());
        return buffer.toString();
    }
}
