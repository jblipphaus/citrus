package com.consol.citrus.ws.message;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PushbackInputStream;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.StringTokenizer;

import javax.xml.soap.MessageFactory;
import javax.xml.soap.MimeHeaders;
import javax.xml.soap.SOAPException;

import org.springframework.util.StringUtils;
import org.springframework.ws.WebServiceMessage;
import org.springframework.ws.soap.SoapMessageCreationException;
import org.springframework.ws.soap.saaj.SaajSoapMessageFactory;
import org.springframework.ws.transport.TransportConstants;
import org.springframework.ws.transport.TransportInputStream;

/**
 * An extension of SaajSoapMessageFactory to create TransportHeaderAwareSaajSoapMessage objects.
 */
public class TransportHeaderAwareSaajSoapMessageFactory extends SaajSoapMessageFactory implements
        TransportHeaderAwareSoapMessageFactory {
    
    private boolean langAttributeOnSoap11FaultString = true;
    
    /**
	 * @see org.springframework.ws.soap.saaj.SaajSoapMessageFactory#SaajSoapMessageFactory()
	 */
    public TransportHeaderAwareSaajSoapMessageFactory() {
    }

    /**
	 * @see org.springframework.ws.soap.saaj.SaajSoapMessageFactory#SaajSoapMessageFactory(MessageFactory)
	 */
    public TransportHeaderAwareSaajSoapMessageFactory(MessageFactory messageFactory) {
        this.setMessageFactory(messageFactory);
    }

    /**
	 * @see org.springframework.ws.soap.saaj.SaajSoapMessageFactory#createWebServiceMessage()
	 */
    @Override
    public WebServiceMessage createWebServiceMessage() {
        try {
            return new TransportHeaderAwareSaajSoapMessage(this.getMessageFactory().createMessage(), langAttributeOnSoap11FaultString);
        }
        catch (SOAPException ex) {
            throw new SoapMessageCreationException("Could not create empty message: " + ex.getMessage(), ex);
        }
    }

    /**
	 * @see org.springframework.ws.soap.saaj.SaajSoapMessageFactory#createWebServiceMessage(InputStream)
	 */
    @Override
    public WebServiceMessage createWebServiceMessage(InputStream inputStream) throws IOException {
        MimeHeaders mimeHeaders = parseMimeHeaders(inputStream);
        Map<String, String> transportHeaders = parseTransportHeaders(inputStream);
        
        try {
            inputStream = checkForUtf8ByteOrderMark(inputStream);
            TransportHeaderAwareSaajSoapMessage message = new TransportHeaderAwareSaajSoapMessage(this.getMessageFactory().createMessage(mimeHeaders, inputStream));
            message.addTransportHeaders(transportHeaders);
            return message;
        }
        catch (SOAPException ex) {
            // SAAJ 1.3 RI has a issue with handling multipart XOP content types which contain "startinfo" rather than
            // "start-info", so let's try and do something about it
            String contentType = StringUtils
                    .arrayToCommaDelimitedString(mimeHeaders.getHeader(TransportConstants.HEADER_CONTENT_TYPE));
            if (contentType.indexOf("startinfo") != -1) {
                contentType = contentType.replace("startinfo", "start-info");
                mimeHeaders.setHeader(TransportConstants.HEADER_CONTENT_TYPE, contentType);
                try {
                    TransportHeaderAwareSaajSoapMessage message = new TransportHeaderAwareSaajSoapMessage(this.getMessageFactory().createMessage(mimeHeaders, inputStream),
                            langAttributeOnSoap11FaultString);
                    message.addTransportHeaders(parseTransportHeaders(inputStream));
                    return message;
                }
                catch (SOAPException e) {
                    // fall-through
                }
            }
            throw new SoapMessageCreationException("Could not create message from InputStream: " + ex.getMessage(), ex);
        }
    }
    
    private Map<String, String> parseTransportHeaders(InputStream inputStream) throws IOException {
        Map<String, String> headers = new LinkedHashMap<String, String>();
        if (inputStream instanceof TransportInputStream) {
            TransportInputStream transportInputStream = (TransportInputStream)inputStream;
            for (Iterator<?> headerNames = transportInputStream.getHeaderNames(); headerNames.hasNext();) {
                String headerName = (String)headerNames.next();
                for (Iterator<?> headerValues = transportInputStream.getHeaders(headerName); headerValues.hasNext();) {
                    headers.put(headerName, (String)headerValues.next());
                }
            }
        }
        return headers;
    }
    
    private MimeHeaders parseMimeHeaders(InputStream inputStream) throws IOException {
        MimeHeaders mimeHeaders = new MimeHeaders();
        if (inputStream instanceof TransportInputStream) {
            TransportInputStream transportInputStream = (TransportInputStream) inputStream;
            for (Iterator<?> headerNames = transportInputStream.getHeaderNames(); headerNames.hasNext();) {
                String headerName = (String) headerNames.next();
                for (Iterator<?> headerValues = transportInputStream.getHeaders(headerName); headerValues.hasNext();) {
                    String headerValue = (String) headerValues.next();
                    StringTokenizer tokenizer = new StringTokenizer(headerValue, ",");
                    while (tokenizer.hasMoreTokens()) {
                        mimeHeaders.addHeader(headerName, tokenizer.nextToken().trim());
                    }
                }
            }
        }
        return mimeHeaders;
    }
    
    private InputStream checkForUtf8ByteOrderMark(InputStream inputStream) throws IOException {
        PushbackInputStream pushbackInputStream = new PushbackInputStream(new BufferedInputStream(inputStream), 3);
        byte[] bom = new byte[3];
        if (pushbackInputStream.read(bom) != -1) {
            // check for the UTF-8 BOM, and remove it if there. See SWS-393
            if (!(bom[0] == (byte) 0xEF && bom[1] == (byte) 0xBB && bom[2] == (byte) 0xBF)) {
                pushbackInputStream.unread(bom);
            }
        }
        return pushbackInputStream;
    }
    
    /**
	 * @see org.springframework.ws.soap.saaj.SaajSoapMessageFactory#setlangAttributeOnSoap11FaultString(boolean)
	 */
    @Override
    public void setlangAttributeOnSoap11FaultString(boolean langAttributeOnSoap11FaultString) {
        super.setlangAttributeOnSoap11FaultString(langAttributeOnSoap11FaultString);
        this.langAttributeOnSoap11FaultString = langAttributeOnSoap11FaultString;
    }
    
    /**
	 * @see org.springframework.ws.soap.saaj.SaajSoapMessageFactory#toString()
	 */
    @Override
    public String toString() {
        StringBuffer buffer = new StringBuffer("TransportHeaderAware");
        buffer.append(super.toString());
        return buffer.toString();
    }
}
