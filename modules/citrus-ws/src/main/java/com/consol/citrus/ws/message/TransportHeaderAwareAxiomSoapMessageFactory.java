package com.consol.citrus.ws.message;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import org.apache.axiom.attachments.Attachments;
import org.apache.axiom.om.OMException;
import org.apache.axiom.om.impl.MTOMConstants;
import org.apache.axiom.soap.SOAP11Constants;
import org.apache.axiom.soap.SOAP12Constants;
import org.apache.axiom.soap.SOAPFactory;
import org.apache.axiom.soap.SOAPMessage;
import org.apache.axiom.soap.impl.builder.MTOMStAXSOAPModelBuilder;
import org.apache.axiom.soap.impl.builder.StAXSOAPModelBuilder;
import org.apache.axiom.soap.impl.llom.soap11.SOAP11Factory;
import org.apache.axiom.soap.impl.llom.soap12.SOAP12Factory;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.ws.WebServiceMessage;
import org.springframework.ws.soap.SoapVersion;
import org.springframework.ws.soap.axiom.AxiomSoapMessageCreationException;
import org.springframework.ws.soap.axiom.AxiomSoapMessageFactory;
import org.springframework.ws.soap.support.SoapUtils;
import org.springframework.ws.transport.TransportConstants;
import org.springframework.ws.transport.TransportInputStream;

/**
 * An extension of AxiomSoapMessageFactory to create TransportHeaderAwareAxiomSoapMessage objects.
 */
public class TransportHeaderAwareAxiomSoapMessageFactory extends
		AxiomSoapMessageFactory implements
		TransportHeaderAwareSoapMessageFactory {
    
    private static final String MULTI_PART_RELATED_CONTENT_TYPE = "multipart/related";

    private static final Log logger = LogFactory.getLog(TransportHeaderAwareAxiomSoapMessageFactory.class);

    private XMLInputFactory inputFactory;

    private boolean payloadCaching = true;

    private boolean attachmentCaching = false;

    private File attachmentCacheDir;

    private int attachmentCacheThreshold = 4096;

    // use SOAP 1.1 by default
    private SOAPFactory soapFactory = new SOAP11Factory();

    private boolean langAttributeOnSoap11FaultString = true;

    /** 
     * @see org.springframework.ws.soap.axiom.AxiomSoapMessageFactory#AxiomSoapMessageFactory()
     */
    public TransportHeaderAwareAxiomSoapMessageFactory() {
    	super();
        inputFactory = XMLInputFactory.newInstance();
    }

    /** 
     * @see org.springframework.ws.soap.axiom.AxiomSoapMessageFactory#setPayloadCaching(boolean)
     */
    @Override
    public void setPayloadCaching(boolean payloadCaching) {
        super.setPayloadCaching(payloadCaching);
        this.payloadCaching = payloadCaching;
    }

    /** 
     * @see org.springframework.ws.soap.axiom.AxiomSoapMessageFactory#setAttachmentCaching(boolean)
     */
    @Override
    public void setAttachmentCaching(boolean attachmentCaching) {
        super.setAttachmentCaching(attachmentCaching);
        this.attachmentCaching = attachmentCaching;
    }

    /** 
     * @see org.springframework.ws.soap.axiom.AxiomSoapMessageFactory#setAttachmentCacheDir(File)
     */
    @Override
    public void setAttachmentCacheDir(File attachmentCacheDir) {
        super.setAttachmentCacheDir(attachmentCacheDir);
        this.attachmentCacheDir = attachmentCacheDir;
    }

    /** 
     * @see org.springframework.ws.soap.axiom.AxiomSoapMessageFactory#setAttachmentCacheThreshold(int)
     */
    @Override
    public void setAttachmentCacheThreshold(int attachmentCacheThreshold) {
        super.setAttachmentCacheThreshold(attachmentCacheThreshold);
        this.attachmentCacheThreshold = attachmentCacheThreshold;
    }

    /** 
     * @see org.springframework.ws.soap.axiom.AxiomSoapMessageFactory#setSoapVersion(SoapVersion)
     */
    @Override
    public void setSoapVersion(SoapVersion version) {
        super.setSoapVersion(version);
        if (SoapVersion.SOAP_11 == version) {
            soapFactory = new SOAP11Factory();
        }
        else if (SoapVersion.SOAP_12 == version) {
            soapFactory = new SOAP12Factory();
        }
        else {
            throw new IllegalArgumentException(
                    "Invalid version [" + version + "]. " + "Expected the SOAP_11 or SOAP_12 constant");
        }
    }

    /** 
     * @see org.springframework.ws.soap.axiom.AxiomSoapMessageFactory#setlangAttributeOnSoap11FaultString(boolean)
     */
    @Override
    public void setlangAttributeOnSoap11FaultString(boolean langAttributeOnSoap11FaultString) {
        super.setlangAttributeOnSoap11FaultString(langAttributeOnSoap11FaultString);
        this.langAttributeOnSoap11FaultString = langAttributeOnSoap11FaultString;
    }
    
    /** 
     * @see org.springframework.ws.soap.axiom.AxiomSoapMessageFactory#createWebServiceMessage()
     */
    @Override
    public WebServiceMessage createWebServiceMessage() {
        return new TransportHeaderAwareAxiomSoapMessage(soapFactory, payloadCaching, langAttributeOnSoap11FaultString);
    }

    /** 
     * @see org.springframework.ws.soap.axiom.AxiomSoapMessageFactory#createWebServiceMessage(InputStream)
     */
    @Override
    public WebServiceMessage createWebServiceMessage(InputStream inputStream) throws IOException {
        Assert.isInstanceOf(TransportInputStream.class, inputStream,
                "TransportHeaderAwareAxiomSoapMessageFactory requires a TransportInputStream");
        Map<String, String> transportHeaders = parseTransportHeaders(inputStream);
        TransportInputStream transportInputStream = (TransportInputStream) inputStream;
        String contentType = getHeaderValue(transportInputStream, TransportConstants.HEADER_CONTENT_TYPE);
        if (!StringUtils.hasLength(contentType)) {
            if (logger.isDebugEnabled()) {
                logger.debug("TransportInputStream has no Content-Type header; defaulting to \"" +
                        SoapVersion.SOAP_11.getContentType() + "\"");
            }
            contentType = SoapVersion.SOAP_11.getContentType();
        }
        String soapAction = getHeaderValue(transportInputStream, TransportConstants.HEADER_SOAP_ACTION);
        if (!StringUtils.hasLength(soapAction)) {
            soapAction = SoapUtils.extractActionFromContentType(contentType);
        }
        try {
            if (isMultiPartRelated(contentType)) {
            	TransportHeaderAwareAxiomSoapMessage message = createMultiPartAxiomSoapMessage(inputStream, contentType, soapAction);
            	message.addTransportHeaders(transportHeaders);
                return message;
            }
            else {
            	TransportHeaderAwareAxiomSoapMessage message = (TransportHeaderAwareAxiomSoapMessage)createAxiomSoapMessage(inputStream, contentType, soapAction);
            	message.addTransportHeaders(transportHeaders);
            	return message;
            }
        }
        catch (XMLStreamException ex) {
            throw new AxiomSoapMessageCreationException("Could not parse request: " + ex.getMessage(), ex);
        }
        catch (OMException ex) {
            throw new AxiomSoapMessageCreationException("Could not create message: " + ex.getMessage(), ex);
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

    private String getHeaderValue(TransportInputStream transportInputStream, String header) throws IOException {
        String contentType = null;
        Iterator<?> iterator = transportInputStream.getHeaders(header);
        if (iterator.hasNext()) {
            contentType = (String) iterator.next();
        }
        return contentType;
    }

    private boolean isMultiPartRelated(String contentType) {
        contentType = contentType.toLowerCase(Locale.ENGLISH);
        return contentType.indexOf(MULTI_PART_RELATED_CONTENT_TYPE) != -1;
    }

    /** Creates an TransportHeaderAwareAxiomSoapMessage without attachments. */
    private WebServiceMessage createAxiomSoapMessage(InputStream inputStream, String contentType, String soapAction)
            throws XMLStreamException {
        XMLStreamReader reader = inputFactory.createXMLStreamReader(inputStream, getCharSetEncoding(contentType));
        String envelopeNamespace = getSoapEnvelopeNamespace(contentType);
        StAXSOAPModelBuilder builder = new StAXSOAPModelBuilder(reader, soapFactory, envelopeNamespace);
        SOAPMessage soapMessage = builder.getSoapMessage();
        return new TransportHeaderAwareAxiomSoapMessage(soapMessage, soapAction, payloadCaching, langAttributeOnSoap11FaultString);
    }

    /** Creates an TransportHeaderAwareAxiomSoapMessage with attachments. */
    private TransportHeaderAwareAxiomSoapMessage createMultiPartAxiomSoapMessage(InputStream inputStream,
                                                             String contentType,
                                                             String soapAction) throws XMLStreamException {
        Attachments attachments =
                new Attachments(inputStream, contentType, attachmentCaching, attachmentCacheDir.getAbsolutePath(),
                        Integer.toString(attachmentCacheThreshold));
        XMLStreamReader reader = inputFactory.createXMLStreamReader(attachments.getSOAPPartInputStream(),
                getCharSetEncoding(attachments.getSOAPPartContentType()));
        StAXSOAPModelBuilder builder;
        String envelopeNamespace = getSoapEnvelopeNamespace(contentType);
        if (MTOMConstants.SWA_TYPE.equals(attachments.getAttachmentSpecType()) ||
                MTOMConstants.SWA_TYPE_12.equals(attachments.getAttachmentSpecType())) {
            builder = new StAXSOAPModelBuilder(reader, soapFactory, envelopeNamespace);
        }
        else if (MTOMConstants.MTOM_TYPE.equals(attachments.getAttachmentSpecType())) {
            builder = new MTOMStAXSOAPModelBuilder(reader, attachments, envelopeNamespace);
        }
        else {
            throw new AxiomSoapMessageCreationException(
                    "Unknown attachment type: [" + attachments.getAttachmentSpecType() + "]");
        }
        return new TransportHeaderAwareAxiomSoapMessage(builder.getSoapMessage(), attachments, soapAction, payloadCaching,
                langAttributeOnSoap11FaultString);
    }

    private String getSoapEnvelopeNamespace(String contentType) {
        if (contentType.indexOf(SOAP11Constants.SOAP_11_CONTENT_TYPE) != -1) {
            return SOAP11Constants.SOAP_ENVELOPE_NAMESPACE_URI;
        }
        else if (contentType.indexOf(SOAP12Constants.SOAP_12_CONTENT_TYPE) != -1) {
            return SOAP12Constants.SOAP_ENVELOPE_NAMESPACE_URI;
        }
        else {
            throw new AxiomSoapMessageCreationException("Unknown content type '" + contentType + "'");
        }

    }

    /** 
     * @see org.springframework.ws.soap.axiom.AxiomSoapMessageFactory#toString()
     */
    @Override
    public String toString() {
        StringBuffer buffer = new StringBuffer("TransportHeaderAware");
        buffer.append(super.toString());
        return buffer.toString();
    }
}
