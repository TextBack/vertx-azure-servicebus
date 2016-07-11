package textback.servicebus;

import org.jboss.logging.BasicLogger;
import org.jboss.logging.Logger;
import org.jboss.logging.annotations.*;

import java.security.InvalidKeyException;


/**
 * Logs messages for tg-srv
 */
@MessageLogger(projectCode = "SERVICEBUS_", length = 4)
@ValidIdRanges({
        // FATAL
        @ValidIdRange(min = 0, max = 999),
        // CRITICAL
        @ValidIdRange(min = 2000, max = 2999),
        // ERROR
        @ValidIdRange(min = 3000, max = 3999),
        // WARN
        @ValidIdRange(min = 4000, max = 4999),
        // INFO
        @ValidIdRange(min = 6000, max = 6999),
        // DEBUG
        @ValidIdRange(min = 7000, max = 7999),
        // TRACE
        @ValidIdRange(min = 9000, max = 9999),
})
public interface ServicebusLogger extends BasicLogger {


    static final ServicebusLogger LOG = Logger.getMessageLogger(ServicebusLogger.class, "textback.servicebus");

    @LogMessage(level = Logger.Level.FATAL)
    @Message(format = Message.Format.MESSAGE_FORMAT, id = 0, value = "Mandatory configuration property is not set: {0}. Set it using env variable or JVM property using '-D' argument. ServiceBus integration is not working")
    void invalidConfigPropertyNotSet(String propertyName);

    @LogMessage(level = Logger.Level.FATAL)
    @Message(format = Message.Format.MESSAGE_FORMAT, id = 1,value = "Failed to initialize SecureRandom on your platform")
    void fatalFailedToInitializeSecureRandom(@Cause Throwable e);


    @LogMessage(level = Logger.Level.ERROR)
    @Message(format = Message.Format.MESSAGE_FORMAT, id = 3000, value = "Cannot create SAS token because key is misconfigured: keyName = {0}, key (trimmed) = {1}")
    void invalidSASKey(String keyName, String keyTrimmed, @Cause InvalidKeyException e);

    @LogMessage(level = Logger.Level.ERROR)
    @Message(format = Message.Format.MESSAGE_FORMAT, id = 3001, value = "Cannot create SAS token because of unknown error: keyName = {0}, key (trimmed) = {1}")
    void sasKeyCantBeCreated(String keyName, String keyTrimmed);

    @LogMessage(level = Logger.Level.ERROR)
    @Message(format = Message.Format.MESSAGE_FORMAT, id = 3002, value = "Cannot recreate SAS token because of unknown error: keyName = {0}, key (trimmed) = {1}")
    void sasKeyCantBeRecreated(String keyName, String keyTrimmed);

    @LogMessage(level = Logger.Level.WARN)
    @Message(format = Message.Format.MESSAGE_FORMAT, id = 4000, value = "Cannot send message to ServiceBus because of invalid SAS")
    void cantSendServiceBusMessageBecauseOfEmptySAS();

    @LogMessage(level = Logger.Level.WARN)
    @Message(format = Message.Format.MESSAGE_FORMAT, id = 4001, value = "Cannot send message to ServiceBus because of API error: statusCode = {0}, statusLine = {1}, headers = {2}")
    void cantSendServiceBusMessageBecauseOfAPIError(int code, String message, String headers);

    @LogMessage(level = Logger.Level.WARN)
    @Message(format = Message.Format.MESSAGE_FORMAT, id = 4002, value = "Cannot send message to ServiceBus because of API exception")
    void cantSendServiceBusMessageBecauseOfException(@Cause Throwable e);


    @LogMessage(level = Logger.Level.WARN)
    @Message(format = Message.Format.MESSAGE_FORMAT, id = 4101, value = "Cannot poll message from ServiceBus because of API error: statusCode = {0}, statusLine = {1}, headers: {2} [{3,number,#}]")
    void cantPeekServiceBusMessageBecauseOfAPIError(int code, String message, String headers, int requestId);

    @LogMessage(level = Logger.Level.WARN)
    @Message(format = Message.Format.MESSAGE_FORMAT, id = 4102, value = "Cannot poll message from ServiceBus because of API exception [{0,number,#}] ")
    void cantPeekServiceBusMessageBecauseOfException(int requestId, @Cause Throwable e);

    @LogMessage(level = Logger.Level.WARN)
    @Message(format = Message.Format.MESSAGE_FORMAT, id = 4103, value = "Cannot poll message from ServiceBus because of exception in reading response [{0,number,#}]")
    void cantPeekServiceBusMessageBecauseOfExceptionReadingResponse(int requestId, @Cause Throwable e);

    @LogMessage(level = Logger.Level.INFO)
    @Message(format = Message.Format.MESSAGE_FORMAT, id = 6001, value = "Created new SAS token: keyname = {0}, key (trimmed) = {1}, VALID_TO = {2}")
    void sasKeyCreated(String keyName, String keyTrimmed, String validTo);

    @LogMessage(level = Logger.Level.INFO)
    @Message(format = Message.Format.MESSAGE_FORMAT, id = 6002, value = "Recreated SAS token: keyname = {0}, key (trimmed) = {1}, VALID_TO = {2}")
    void sasKeyRecreated(String keyName, String keyTrimmed, String validTo);

    @LogMessage(level = Logger.Level.INFO)
    @Message(format = Message.Format.MESSAGE_FORMAT, id = 6003, value = "SAS token expired: keyname = {0}, key (trimmed) = {1}, VALID_TO = {2}")
    void sasKeyExpired(String keyName, String keyTrimmed, String validTo);


    @LogMessage(level = Logger.Level.TRACE)
    @Message(format = Message.Format.MESSAGE_FORMAT, id = 9000, value = "Sending message to queue: {0}, Message: {1}")
    void traceSendingMessageToQueue(String queueName, String body);


    @LogMessage(level = Logger.Level.TRACE)
    @Message(format = Message.Format.MESSAGE_FORMAT, id = 9001, value = "Sent message to queue: {0}, Message: {1}, Headers: {2}")
    void traceSentMessageToQueue(String queueName, String body, String headers);


    @LogMessage(level = Logger.Level.TRACE)
    @Message(format = Message.Format.MESSAGE_FORMAT, id = 9010, value = "Started polling queue: {0} [{1,number,#}]")
    void tracePeekingMessageFromQueue(String queueName, int requestId);

    @LogMessage(level = Logger.Level.TRACE)
    @Message(format = Message.Format.MESSAGE_FORMAT, id = 9011, value = "Got message from queue: {0}, Message: {1}, Headers: {2} [{3,number,#}]")
    void tracePeekMessageFromQueue(String queueName, String body, String headers, int requestId);

    @LogMessage(level = Logger.Level.TRACE)
    @Message(format = Message.Format.MESSAGE_FORMAT, id = 9012, value = "Got no message from queue after timeout: {0}, Headers {1} [{2,number,#}] ")
    void tracePeekNoMessageFromQueue(String queueName, String headers, int requestId);

    @LogMessage(level = Logger.Level.TRACE)
    @Message(format = Message.Format.MESSAGE_FORMAT, id = 9013, value = "Deleted message after peek. Code: {0}, Status: {1} [{2,number,#}] ")
    void traceDeletedMessageWithStatusCode(int code, String status, int requestId);

    @LogMessage(level = Logger.Level.TRACE)
    @Message(format = Message.Format.MESSAGE_FORMAT, id = 9014, value = "Unlocked message after peek. Code: {0}, Status: {1} [{2,number,#}] ")
    void traceUnlockedMessageWithStatusCode(int code, String status, int requestId);

}