package textback.servicebus;

/**
 * how verticle should dispatch received message
 */
public enum InboundDispatchMode {

    /**
     * Message should be published to verticle base address for received messages
     */
    PUBLISH_TO_VERTICLE_RECEIVE_ADDRESS,

    /**
     * Message should be sent to verticle base address for received messages
     */
    SEND_TO_VERTICLE_RECEIVE_ADDRESS,

    /**
     * Message should be published to specific address for message id.
     * Address is composed of base receive address + ".messageId." + message correlation id
     */
    PUBLISH_TO_MESSAGE_ID_RECEIVE_ADDRESS,

    /**
     * Message should be sent to specific address for message id.
     * Address is composed of base receive address + ".messageId." + message correlation id
     */
    SEND_TO_MESSAGE_ID_RECEIVE_ADDRESS,

}
