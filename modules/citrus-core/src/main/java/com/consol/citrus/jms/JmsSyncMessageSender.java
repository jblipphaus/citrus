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

package com.consol.citrus.jms;

import javax.jms.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.*;
import org.springframework.integration.Message;
import org.springframework.integration.jms.DefaultJmsHeaderMapper;
import org.springframework.integration.jms.JmsHeaderMapper;
import org.springframework.jms.connection.ConnectionFactoryUtils;
import org.springframework.jms.support.JmsUtils;
import org.springframework.jms.support.converter.MessageConverter;
import org.springframework.jms.support.converter.SimpleMessageConverter;
import org.springframework.jms.support.destination.DynamicDestinationResolver;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import com.consol.citrus.exceptions.CitrusRuntimeException;
import com.consol.citrus.message.*;

/**
 * Synchronous message sender implementation for JMS. Sender publishes messages to a JMS destination and
 * sets the reply destination in the request message. Sender consumes the reply destination right away and
 * invokes a reply message handler implementation with this reply message.
 * 
 * Class can either define a static reply destination or a temporary reply destination.
 * 
 * @author Christoph Deppisch
 */
public class JmsSyncMessageSender implements MessageSender, BeanNameAware, InitializingBean, DisposableBean {
    /** JMS connection factory */
    private ConnectionFactory connectionFactory;
    
    /** JMS connection */
    private Connection connection = null;
    
    /** JMS session */
    private Session session = null;
    
    /** Destination instance */
    private Destination destination;
    
    /** Destination name */
    private String destinationName;
    
    /** Reply destination */
    private Destination replyDestination;
    
    /** Reply destination name */
    private String replyDestinationName;
    
    /** Reply message handler */
    private ReplyMessageHandler replyMessageHandler;
    
    /** The message converter */
    private MessageConverter messageConverter = new SimpleMessageConverter();

    /** The header mapper */
    private JmsHeaderMapper headerMapper = new DefaultJmsHeaderMapper();
    
    /** Time to synchronously wait for reply */
    private long replyTimeout = 5000L;
    
    /** Reply message corelator */
    private ReplyMessageCorrelator correlator = null;
    
    /** Use JMS topics instead of queues */
    private boolean pubSubDomain = false;
    
    /** Message sender name */
    private String name;
    
    /**
     * Logger
     */
    private static Logger log = LoggerFactory.getLogger(JmsSyncMessageSender.class);
    
    /**
     * @see com.consol.citrus.message.MessageSender#send(org.springframework.integration.Message)
     * @throws CitrusRuntimeException
     */
    public void send(Message<?> message) {
        Assert.notNull(message, "Message is empty - unable to send empty message");
        
        String defaultDestinationName = getDefaultDestinationName();
        
        log.info("Sending JMS message to destination: '" + defaultDestinationName + "'");

        if (log.isDebugEnabled()) {
            log.debug("Message to send is:\n" + message.toString());
        }

        MessageProducer messageProducer = null;
        MessageConsumer messageConsumer = null;
        Destination replyToDestination = null;
        
        try {
            if(connection == null) { 
                connection = createConnection();
            }
            
            if(session == null) {
                session = createSession(connection);
            }
            
            JmsMessageConverter jmsMessageConverter = new JmsMessageConverter(messageConverter, headerMapper);
            javax.jms.Message jmsRequest = jmsMessageConverter.toMessage(message, session);
            
            messageProducer = session.createProducer(getDefaultDestination(session));

            replyToDestination = getReplyDestination(session, message);
            jmsRequest.setJMSReplyTo(replyToDestination);

            if (replyToDestination instanceof TemporaryQueue || replyToDestination instanceof TemporaryTopic) {
                messageConsumer = session.createConsumer(replyToDestination);
            } else if(replyToDestination instanceof Queue) {
                String messageId = jmsRequest.getJMSMessageID().replaceAll("'", "''");
                String messageSelector = "JMSCorrelationID = '" + messageId + "'";
                messageConsumer = session.createConsumer(replyToDestination, messageSelector);
            } else {
                String messageId = jmsRequest.getJMSMessageID().replaceAll("'", "''");
                String messageSelector = "JMSCorrelationID = '" + messageId + "'";
                messageConsumer = session.createDurableSubscriber((Topic)replyToDestination, name, messageSelector, false);
            }
            
            messageProducer.send(jmsRequest);
            
            log.info("Message was successfully sent to destination: '" + defaultDestinationName + "'");
            
            javax.jms.Message jmsReplyMessage = (this.replyTimeout >= 0) ? messageConsumer.receive(replyTimeout) : messageConsumer.receive();
            
            if(replyMessageHandler != null) {
                if(correlator != null) {
                    replyMessageHandler.onReplyMessage((Message<?>)jmsMessageConverter.fromMessage(jmsReplyMessage),
                        correlator.getCorrelationKey(message));
                } else {
                    replyMessageHandler.onReplyMessage((Message<?>)jmsMessageConverter.fromMessage(jmsReplyMessage));
                }
            }
        } catch (JMSException e) {
            throw new CitrusRuntimeException(e);
        } finally {
            JmsUtils.closeMessageProducer(messageProducer);
            JmsUtils.closeMessageConsumer(messageConsumer);
            deleteTemporaryDestination(replyToDestination);
        }
    }
    
    /**
     * Delete temporary destinations.
     * @param destination
     */
    private void deleteTemporaryDestination(Destination destination) {
        try {
            if (destination instanceof TemporaryQueue) { 
                ((TemporaryQueue) destination).delete();
            } else if (destination instanceof TemporaryTopic) {
                ((TemporaryTopic) destination).delete();
            }
        } catch (JMSException e) {
            log.error("Error while deleting temporary destination", e);
        }
    }

    /**
     * Retrieve the reply destination either by injected instance, destination name or
     * by creating a new temporary destination.
     * 
     * @param session current JMS session
     * @param message holding possible reply destination in header.
     * @return the reply destination.
     * @throws JMSException
     */
    private Destination getReplyDestination(Session session, Message<?> message) throws JMSException {
        if(message.getHeaders().getReplyChannel() != null) {
            if(message.getHeaders().getReplyChannel() instanceof Destination) {
                return (Destination)message.getHeaders().getReplyChannel();
            } else {
                return resolveDestinationName(message.getHeaders().getReplyChannel().toString(), session);
            }
        } else if (replyDestination != null) {
            return replyDestination;
        } else if (StringUtils.hasText(replyDestinationName)) {
            return resolveDestinationName(this.replyDestinationName, session);
        }
        
        if(pubSubDomain && session instanceof TopicSession){
            return session.createTemporaryTopic();
        } else {
            return session.createTemporaryQueue();
        }
    }

    /**
     * Get send destination either from injected destination instance or by resolving
     * a destination name.
     *  
     * @param session current JMS session
     * @return the destination.
     * @throws JMSException
     */
    private Destination getDefaultDestination(Session session) throws JMSException {
        if (destination != null) {
            return destination;
        }
        
        return resolveDestinationName(destinationName, session);
    }
    
    /**
     * 
     * @param destinationName
     * @return
     */
    private Destination resolveDestinationName(String name, Session session) throws JMSException {
        return new DynamicDestinationResolver().resolveDestinationName(session, name, pubSubDomain);
    }
    
    /**
     * Get the destination name (either queue name or topic name).
     * @return the destinationName
     */
    protected String getDefaultDestinationName() {
        try {
            if(destination != null) {
                if(destination instanceof Queue) {
                    return ((Queue)destination).getQueueName();
                } else if(destination instanceof Topic) {
                    return ((Topic)destination).getTopicName();
                } else {
                    return destination.toString();
                }
            } else {
                return destinationName;
            }
        } catch (JMSException e) {
            log.error("Error while getting destination name", e);
            return "";
        }
    }
    
    /**
     * Create new JMS connection.
     * @return connection
     * @throws JMSException
     */
    protected Connection createConnection() throws JMSException {
        if(connection == null) {
            if (!pubSubDomain && connectionFactory instanceof QueueConnectionFactory) {
                connection = ((QueueConnectionFactory) connectionFactory).createQueueConnection();
            } else if(pubSubDomain && connectionFactory instanceof TopicConnectionFactory) {
                connection = ((TopicConnectionFactory) connectionFactory).createTopicConnection();
            } else {
                log.warn("Not able to create a connection with connection factory '" + connectionFactory + "'" +
                        " when using setting 'publish-subscribe-domain' (=" + pubSubDomain + ")");
                
                connection = connectionFactory.createConnection();
            }
        }
        
        return connection;
    }
    
    /**
     * Create new JMS session.
     * @param connection to use for session creation.
     * @return session.
     * @throws JMSException
     */
    protected Session createSession(Connection connection) throws JMSException {
        if(session == null) {
            if (!pubSubDomain && connection instanceof QueueConnection) {
                session = ((QueueConnection) connection).createQueueSession(false, Session.AUTO_ACKNOWLEDGE);
            } else if(pubSubDomain && connectionFactory instanceof TopicConnectionFactory) {
                connection.setClientID(name);
                session = ((TopicConnection) connection).createTopicSession(false, Session.AUTO_ACKNOWLEDGE);
            } else {
                log.warn("Not able to create a session with connection factory '" + connectionFactory + "'" +
                        " when using setting 'publish-subscribe-domain' (=" + pubSubDomain + ")");
                
                session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            }
        }
        
        return session;
    }
    
    /**
     * @see org.springframework.beans.factory.InitializingBean#afterPropertiesSet()
     */
    public void afterPropertiesSet() throws Exception {
        createConnection();
        createSession(connection);
        
        connection.start();
    }
    
    /**
     * Destroy method closing JMS session and connection
     */
    public void destroy() throws Exception {
        JmsUtils.closeSession(session);
        
        if(connection != null) {
            ConnectionFactoryUtils.releaseConnection(connection, this.connectionFactory, true);
        }
    }
    
    /**
     * Set the connection factory.
     * @param connectionFactory the connectionFactory to set
     */
    public void setConnectionFactory(ConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
    }

    /**
     * Set the reply message handler.
     * @param replyMessageHandler the replyMessageHandler to set
     */
    public void setReplyMessageHandler(ReplyMessageHandler replyMessageHandler) {
        this.replyMessageHandler = replyMessageHandler;
    }

    /**
     * Get the reply message handler.
     * @return the replyMessageHandler
     */
    public ReplyMessageHandler getReplyMessageHandler() {
        return replyMessageHandler;
    }

    /**
     * Set the send destination.
     * @param destination the destination to set
     */
    public void setDestination(Destination destination) {
        this.destination = destination;
    }

    /**
     * Set the send destination name.
     * @param destinationName the destinationName to set
     */
    public void setDestinationName(String destinationName) {
        this.destinationName = destinationName;
    }

    /**
     * Set the reply destination.
     * @param replyDestination the replyDestination to set
     */
    public void setReplyDestination(Destination replyDestination) {
        this.replyDestination = replyDestination;
    }

    /**
     * Set the reply destination name.
     * @param replyDestinationName the replyDestinationName to set
     */
    public void setReplyDestinationName(String replyDestinationName) {
        this.replyDestinationName = replyDestinationName;
    }

    /**
     * Set the reply message timeout.
     * @param replyTimeout the replyTimeout to set
     */
    public void setReplyTimeout(long replyTimeout) {
        this.replyTimeout = replyTimeout;
    }

    /**
     * Set the reply message correlator.
     * @param correlator the correlator to set
     */
    public void setCorrelator(ReplyMessageCorrelator correlator) {
        this.correlator = correlator;
    }

    /**
     * Set whether to use JMS topics instead of JMS queues.
     * @param pubSubDomain the pubSubDomain to set
     */
    public void setPubSubDomain(boolean pubSubDomain) {
        this.pubSubDomain = pubSubDomain;
    }

    /**
     * Is this sender using JMS topics instead of JMS queues.
     * @return the pubSubDomain
     */
    public boolean isPubSubDomain() {
        return pubSubDomain;
    }

    /**
     * @see org.springframework.beans.factory.BeanNameAware#setBeanName(java.lang.String)
     */
    public void setBeanName(String name) {
        this.name = name;
    }
}
