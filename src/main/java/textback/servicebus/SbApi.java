package textback.servicebus;

import com.microsoft.applicationinsights.TelemetryClient;
import com.microsoft.applicationinsights.internal.schemav2.DependencyKind;
import com.microsoft.applicationinsights.telemetry.Duration;
import com.microsoft.applicationinsights.telemetry.MetricTelemetry;
import com.microsoft.applicationinsights.telemetry.RemoteDependencyTelemetry;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.*;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import org.apache.commons.lang3.EnumUtils;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.StopWatch;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeoutException;

import static textback.servicebus.ServicebusLogger.LOG;


/**
 * Sends messages to service bus when message received. Currently supports only one queue
 */
@SuppressWarnings({"UnusedDeclaration", "UnnecessaryReturnStatement"})
public class SbApi extends AbstractVerticle {

    /**
     * Basic vertx event bus address to received messages from
     */
    public final static String DEFAULT_BASE_ADDRESS = "textback.servicebus.";

    public final static String DEFAULT_SEND_MESSAGE_ADDRESS = DEFAULT_BASE_ADDRESS + "sendMessage";

    public final static String DEFAULT_RECEIVED_MESSAGE_ADDRESS = DEFAULT_BASE_ADDRESS + "receivedMessage";

    private static final String SERVICEBUS_BASE_DOMAIN = ".servicebus.windows.net";

    /**
     * Azure default lock time is 60s, so give 5 sec to respond
     */
    public static final int DEFAULT_EVENTBUS_TIMEOUT = 55000;

    /**
     * Event bus address where received messages are sent to
     */
    String receiveAddress = DEFAULT_RECEIVED_MESSAGE_ADDRESS;

    /**
     * Event bus address which is listened for messages that should be sent to Azure ServiceBus
     */
    String sendAddress = DEFAULT_SEND_MESSAGE_ADDRESS;

    private EventBus eventBus;

    String listenQueueName;

    String sendQueueName;

    String keyName;

    String key;

    String namespace;

    Sas sas;

    /**
     * long-poll peek timeout
     */
    long peekTimeout = 10000;

    long errorReconnectPause = 3000;

    /**
     * Wait for response timeout
     */
    long responseTimeout = 13000;

    long eventbusTimeout;

    EventbusTimeoutAction defaultEventbusTimeoutAction;

    // tmp var
    private String baseQueueAddress;

    TelemetryClient telemetryClient;

    private HttpClient httpSendClient;

    private HttpClient httpPollClient;

    InboundDispatchMode inboundDispatchMode = InboundDispatchMode.SEND_TO_VERTICLE_RECEIVE_ADDRESS;

    Random random = new Random();

    public SbApi() {
    }

    @Override
    public void init(Vertx vertx, Context context) {
        super.init(vertx, context);
        this.eventBus = vertx.eventBus();
        telemetryClient = new TelemetryClient();
        this.readConfig(context.config());
    }

    /**
     * Supported config:
     * <ul>
     * <li>AZURE_SB_KEYNAME - servicebus access key name; system, env; required</li>
     * <li>AZURE_SB_KEY - servicebus access key; system, env; required</li>
     * <li>AZURE_SB_NAMESPACE - servicebus namespace; system, env; required</li>
     * <li>AZURE_SB_LISTEN_QUEUE_NAME - servicebus queue to listen; config, system, env; optional</li>
     * <li>EB_RECEIVE_ADDRESS - eventbus address to which received messages are sent; config, default; optional</li>
     * <li>EB_SEND_ADDRESS - eventbus address which is listened for messages to be sent to Azure ServiceBus; config, default; optional</li>
     * <li>AZURE_SB_INBOUND_DISPATCH_MODE - how verticle should dispatch received message; config, system, env; optional</li>
     * </ul>
     *
     * @param config verticle config
     */
    public void readConfig(JsonObject config) {
        keyName = System.getProperty("AZURE_SB_KEYNAME", System.getenv("AZURE_SB_KEYNAME"));
        key = System.getProperty("AZURE_SB_KEY", System.getenv("AZURE_SB_KEY"));
        namespace = System.getProperty("AZURE_SB_NAMESPACE", System.getenv("AZURE_SB_NAMESPACE"));
        if (config.containsKey("AZURE_SB_LISTEN_QUEUE_NAME")) {
            listenQueueName = config.getString("AZURE_SB_LISTEN_QUEUE_NAME");
        } else {
            listenQueueName = System.getProperty("AZURE_SB_LISTEN_QUEUE_NAME", System.getenv("AZURE_SB_LISTEN_QUEUE_NAME"));
        }
        if (config.containsKey("EB_RECEIVE_ADDRESS")) {
            receiveAddress = config.getString("EB_RECEIVE_ADDRESS");
        }
        if (config.containsKey("AZURE_SB_SEND_QUEUE_NAME")) {
            sendQueueName = config.getString("AZURE_SB_SEND_QUEUE_NAME");
        } else {
            sendQueueName = System.getProperty("AZURE_SB_SEND_QUEUE_NAME", System.getenv("AZURE_SB_SEND_QUEUE_NAME"));
        }
        if (config.containsKey("EB_SEND_ADDRESS")) {
            sendAddress = config.getString("EB_SEND_ADDRESS");
        }
        if (config.containsKey("AZURE_SB_INBOUND_DISPATCH_MODE")) {
            inboundDispatchMode = InboundDispatchMode.valueOf(config.getString("AZURE_SB_INBOUND_DISPATCH_MODE"));
        }
        defaultEventbusTimeoutAction = EnumUtils.getEnum(EventbusTimeoutAction.class, getConfigProperty("AZURE_SB_DEFAULT_EVENTBUS_TIMEOUT_ACTION", config, EventbusTimeoutAction.RELEASE_LOCK.toString()));

        eventbusTimeout = Long.parseLong(getConfigProperty("AZURE_SB_EVENTBUS_TIMEOUT", config, String.valueOf(DEFAULT_EVENTBUS_TIMEOUT)));

        boolean invalidConfig = false;
        if (keyName == null) {
            LOG.invalidConfigPropertyNotSet("AZURE_SB_KEYNAME");
            invalidConfig = true;
        }
        if (key == null) {
            LOG.invalidConfigPropertyNotSet("AZURE_SB_KEY");
            invalidConfig = true;
        }
        if (namespace == null) {
            LOG.invalidConfigPropertyNotSet("AZURE_SB_NAMESPACE");
            invalidConfig = true;
        }
        if (listenQueueName == null) {
            LOG.invalidConfigPropertyNotSet("AZURE_SB_LISTEN_QUEUE_NAME");
        }
        if (sendQueueName == null) {
            LOG.invalidConfigPropertyNotSet("AZURE_SB_SEND_QUEUE_NAME");
        }

        if (invalidConfig) {
            throw new RuntimeException("Invalid Config");
        }

        baseQueueAddress = "https://" + namespace + SERVICEBUS_BASE_DOMAIN + "/";
    }

    private String getConfigProperty(String name, JsonObject config, String defaultValue) {
        if (config.containsKey(name)) {
            return config.getString(name);
        } else {
            String value = System.getProperty(name);
            if (value == null) {
                if (System.getenv().containsKey(name)) {
                    value = System.getenv(name);
                }
            }
            if (value == null) {
                value = defaultValue;
            }
            return value;
        }
    }


    @Override
    public void start() throws Exception {
        registerHandlers();
        recreateSendClient();
        recreatePollClient();
        if (!StringUtils.isEmpty(listenQueueName)) {
            peekFromQueue();
        }
    }

    private void recreatePollClient() {
        if (httpPollClient != null) {
            try {
                httpPollClient.close();
            } catch (Exception ignored) {
            }
        }
        httpPollClient = vertx.createHttpClient(new HttpClientOptions().setSsl(true).setMaxPoolSize(1)
                .setKeepAlive(true)
                .setVerifyHost(false)
        );
    }

    private void recreateSendClient() {
        if (httpSendClient != null) {
            try {
                httpSendClient.close();
            } catch (Exception ignored) {
            }
        }
        httpSendClient = vertx.createHttpClient(new HttpClientOptions().setSsl(true).setMaxPoolSize(1)
                .setKeepAlive(true)
                .setVerifyHost(false)
        );
    }

    private void peekFromQueue() {
        String requestId = RandomStringUtils.randomAlphanumeric(8);
        // if this request has been rescheduled after end or error
        final boolean[] rescheduled = {false};
        checkSas();
        if (sas == null) {
            if (!rescheduled[0]) {
                rescheduled[0] = true;
                peekFromQueueAfterPause();
            }
            return;
        }
        String apiUri = "/" + listenQueueName + "/messages/head" + "?timeout=" + peekTimeout / 1000;

        LOG.tracePeekingMessageFromQueue(listenQueueName, requestId);
        StopWatch stopWatch = new StopWatch();
        stopWatch.start();
        httpPollClient.post(443, namespace + SERVICEBUS_BASE_DOMAIN, apiUri, (httpClientResponse) -> {
            StringBuilder headers = new StringBuilder();
            httpClientResponse.headers().forEach(
                    (entry) -> headers.append(entry.getKey()).append(" = ").append(entry.getValue()).append("; ")
            );
            stopWatch.stop();
            if (httpClientResponse.statusCode() == 201) {
                String messageUri = httpClientResponse.headers().get("location");
                // peeked
                httpClientResponse.bodyHandler((buffer) -> {
                    String body = buffer.toString(StandardCharsets.UTF_8.name());
                    LOG.tracePeekMessageFromQueue(listenQueueName, body, headers.toString(), requestId);
                    telemetryClient.getContext().getTags().put("queueName", listenQueueName);
                    telemetryClient.trackDependency("sb.queue", "peek", new Duration(stopWatch.getTime()), true);
                    telemetryClient.getContext().getTags().remove("queueName");
                    DeliveryOptions deliveryOptions = new DeliveryOptions();
                    deliveryOptions.addHeader("queueName", listenQueueName);
                    httpClientResponse.headers().entries().forEach((entry) -> deliveryOptions.addHeader(entry.getKey(), entry.getValue()));
                    String address = receiveAddress;
                    if (inboundDispatchMode == InboundDispatchMode.PUBLISH_TO_MESSAGE_ID_RECEIVE_ADDRESS ||
                            inboundDispatchMode == InboundDispatchMode.SEND_TO_MESSAGE_ID_RECEIVE_ADDRESS) {
                        address = receiveAddress + ".messageId." + deliveryOptions.getHeaders().get("messageId");
                    }
                    if (inboundDispatchMode == InboundDispatchMode.SEND_TO_MESSAGE_ID_RECEIVE_ADDRESS ||
                            inboundDispatchMode == InboundDispatchMode.SEND_TO_VERTICLE_RECEIVE_ADDRESS) {
                        StopWatch eventbusProcessingTimer = new StopWatch();
                        eventbusProcessingTimer.start();
                        final String finalAddress = address;
                        eventBus.send(address, body, deliveryOptions, (AsyncResult<Message<Object>> result) -> {
                            eventbusProcessingTimer.stop();
                            MetricTelemetry mt = new MetricTelemetry("eb." + finalAddress + ".processing_time", stopWatch.getTime());
                            mt.getContext().getOperation().setId(String.valueOf(requestId));
                            if (result.succeeded()) {
                                mt.getProperties().put("success", "true");
                                telemetryClient.trackMetric(mt);
                                deleteMessage(requestId, messageUri);
                            } else {
                                mt.getProperties().put("success", "false");
                                mt.getProperties().put("cause", result.cause().toString());
                                telemetryClient.trackMetric(mt);
                                if (result.cause() instanceof ReplyException) {
                                    ReplyException replyException = (ReplyException) result.cause();
                                    if (replyException.failureType() == ReplyFailure.TIMEOUT) {
                                        switch (defaultEventbusTimeoutAction) {
                                            case DELETE: {
                                                LOG.traceDeletedMessageBecauseOfEventbusTimeout(requestId);
                                                deleteMessage(requestId, messageUri);
                                                break;
                                            }
                                            case RELEASE_LOCK:
                                            default: {
                                                LOG.traceReleaseLockOnMessageBecauseOfEventbusTimeout(requestId);
                                                releaseLock(requestId, messageUri);
                                            }
                                        }
                                    } else {
                                        releaseLock(requestId, messageUri);
                                    }
                                } else {
                                    releaseLock(requestId, messageUri);
                                }
                            }
                        });
                    } else {
                        eventBus.publish(address, body, deliveryOptions);
                        deleteMessage(requestId, messageUri);
                    }
                    if (!rescheduled[0]) {
                        rescheduled[0] = true;
                        peekFromQueue();
                    }
                    return;
                });
                httpClientResponse.exceptionHandler((e) -> {
                    RemoteDependencyTelemetry telemetry = new RemoteDependencyTelemetry("sb.queue", "peek", new Duration(stopWatch.getTime()), false);
                    telemetryClient.getContext().getTags().put("queueName", listenQueueName);
                    telemetryClient.trackDependency(telemetry);
                    telemetryClient.getContext().getTags().remove("queueName");
                    LOG.cantPeekServiceBusMessageBecauseOfExceptionReadingResponse(requestId, e);
                    if (e instanceof TimeoutException) {
                        // reconnect client
                        recreatePollClient();
                    }
                    if (!rescheduled[0]) {
                        rescheduled[0] = true;
                        peekFromQueueAfterPause();
                    }
                    return;
                });
            } else if (httpClientResponse.statusCode() == 204) {
                LOG.tracePeekNoMessageFromQueue(listenQueueName, headers.toString(), requestId);
                telemetryClient.trackDependency("sb.queue", "peek", new Duration(stopWatch.getTime()), true);
                if (!rescheduled[0]) {
                    rescheduled[0] = true;
                    peekFromQueue();
                }
                return;
            } else {
                RemoteDependencyTelemetry telemetry = new RemoteDependencyTelemetry("sb.queue", "peek", new Duration(stopWatch.getTime()), false);
                telemetry.setAsync(true);
                telemetry.setDependencyKind(DependencyKind.Http);
                telemetryClient.getContext().getTags().put("queueName", listenQueueName);
                telemetryClient.trackDependency(telemetry);
                telemetryClient.getContext().getTags().remove("queueName");
                LOG.cantPeekServiceBusMessageBecauseOfAPIError(httpClientResponse.statusCode(), httpClientResponse.statusMessage(), headers.toString(), requestId);
                if (!rescheduled[0]) {
                    rescheduled[0] = true;
                    peekFromQueueAfterPause();
                }
                return;
            }
        })
                .exceptionHandler((e) -> {
                    RemoteDependencyTelemetry telemetry = new RemoteDependencyTelemetry("sb.queue", "peek", new Duration(stopWatch.getTime()), false);
                    telemetryClient.getContext().getTags().put("queueName", listenQueueName);
                    telemetryClient.trackDependency(telemetry);
                    telemetryClient.getContext().getTags().remove("queueName");
                    LOG.cantPeekServiceBusMessageBecauseOfException(requestId, e);
                    if (e instanceof TimeoutException) {
                        // reconnect client
                        recreatePollClient();
                    }
                    if (!rescheduled[0]) {
                        rescheduled[0] = true;
                        peekFromQueueAfterPause();
                    }
                    return;
                })
                .setTimeout(responseTimeout)
                .setChunked(true)
                .putHeader("content-type", "application/json")
                .putHeader("authorization", sas != null ? sas.token : null)
                .end();
    }

    private void releaseLock(String requestId, String messageUri) {
        // release lock
        httpPollClient.putAbs(messageUri, (deleteResponse) ->
                LOG.traceUnlockedMessageWithStatusCode(deleteResponse.statusCode(), deleteResponse.statusMessage(), requestId))
                .putHeader("authorization", sas.token)
                .putHeader("content-length", String.valueOf(0))
                .end();
    }

    private void deleteMessage(String requestId, String messageUri) {
        // delete message
        httpPollClient.deleteAbs(messageUri, (deleteResponse) ->
                LOG.traceDeletedMessageWithStatusCode(deleteResponse.statusCode(), deleteResponse.statusMessage(), requestId))
                .putHeader("authorization", sas.token)
                .putHeader("content-length", String.valueOf(0))
                .end();
    }

    private void peekFromQueueAfterPause() {
        vertx.setTimer(errorReconnectPause, (timerId) -> peekFromQueue());
    }

    private void registerHandlers() {
        eventBus.consumer(sendAddress, this::onSendMessage);
    }

    /**
     * Invoked by eventbus on @SEND_MESSAGE_ADDRESS
     *
     * @param message inbound message
     */
    public void onSendMessage(Message message) {
        if (StringUtils.isEmpty(sendQueueName)) {
            message.fail(500, "AbApi is not configured for sending messages");
            return;
        }
        checkSas();
        if (sas == null) {
            LOG.cantSendServiceBusMessageBecauseOfEmptySAS();
            message.fail(500, "Failed to access message bus: see details in logs");
            return;
        }

        final String sendQueueName = (message.headers().get("queueName") != null) ? message.headers().get("queueName") : this.sendQueueName;

        String apiUri = "/" + sendQueueName + "/messages" + "?api-version=2013-08";
        Map object = Json.decodeValue((String) message.body(), Map.class);
        String body = Json.encode(object);

        LOG.traceSendingMessageToQueue(sendQueueName, body);

        StopWatch stopWatch = new StopWatch();
        stopWatch.start();

        HttpClientRequest request = httpSendClient.post(443, namespace + SERVICEBUS_BASE_DOMAIN, apiUri, (httpClientResponse) -> {
            stopWatch.stop();
            StringBuilder headers = new StringBuilder();
            httpClientResponse.headers().forEach(
                    (entry) -> headers.append(entry.getKey()).append(" = ").append(entry.getValue()).append("; ")
            );
            if (httpClientResponse.statusCode() == 201) {
                // ok
                telemetryClient.getContext().getTags().put("queueName", sendQueueName);
                telemetryClient.trackDependency("sb.queue", "sendMessage", new Duration(stopWatch.getTime()), true);
                telemetryClient.getContext().getTags().remove("queueName");
                LOG.traceSentMessageToQueue(sendQueueName, body, headers.toString());
                message.reply(null);
                return;
            } else {
                RemoteDependencyTelemetry telemetry = new RemoteDependencyTelemetry("sb.queue", "sendMessage", new Duration(stopWatch.getTime()), false);
                telemetry.setAsync(true);
                telemetry.setDependencyKind(DependencyKind.Http);
                telemetryClient.getContext().getTags().put("queueName", sendQueueName);
                telemetryClient.trackDependency(telemetry);
                telemetryClient.getContext().getTags().remove("queueName");
                LOG.cantSendServiceBusMessageBecauseOfAPIError(httpClientResponse.statusCode(), httpClientResponse.statusMessage(), headers.toString());
                message.fail(httpClientResponse.statusCode(), "ServiceBus API error");
                return;
            }
        })
                .exceptionHandler((e) -> {
                    RemoteDependencyTelemetry telemetry = new RemoteDependencyTelemetry("sb.queue", "sendMessage", new Duration(stopWatch.getTime()), false);
                    telemetryClient.getContext().getTags().put("queueName", sendQueueName);
                    telemetryClient.trackDependency(telemetry);
                    telemetryClient.getContext().getTags().remove("queueName");
                    LOG.cantSendServiceBusMessageBecauseOfException(e);
                    if (e instanceof TimeoutException) {
                        // reconnect client
                        recreateSendClient();
                    }
                    message.fail(500, e.toString());
                    return;
                })
                .setChunked(true)
                .putHeader("content-type", "application/json")
                .putHeader("authorization", sas != null ? sas.token : null);
        message.headers().forEach((entry) -> request.putHeader(entry.getKey(), entry.getValue()));
        request.end(body);
    }


    private synchronized void checkSas() {
        if (sas == null) {
            try {
                sas = SasUtils.getSASToken(baseQueueAddress, keyName, key, 30);
            } catch (InvalidKeyException e) {
                LOG.invalidSASKey(keyName, StringUtils.substring(key, 0, 10), e);
            }
            if (sas == null) {
                LOG.sasKeyCantBeCreated(keyName, key);
            } else {
                LOG.sasKeyCreated(keyName, StringUtils.substring(sas.token, 0, 10), sas.validToTime.toString());
            }
        }
        if (sas.isExpired()) {
            LOG.sasKeyExpired(keyName, StringUtils.substring(sas.token, 0, 10), sas.validToTime.toString());
            sas = null;
            try {
                sas = SasUtils.getSASToken(baseQueueAddress, keyName, key, 30);
            } catch (InvalidKeyException e) {
                LOG.invalidSASKey(keyName, StringUtils.substring(key, 0, 10), e);
            }
            if (sas == null) {
                LOG.sasKeyCantBeRecreated(keyName, key);
            } else {
                LOG.sasKeyRecreated(keyName, StringUtils.substring(sas.token, 0, 10), sas.validToTime.toString());
            }
        }
    }
}
