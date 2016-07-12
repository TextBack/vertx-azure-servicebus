vertx-azure-servicebus
======================

Consumer and producer for Azure ServiceBus. Written from scratch using Azure REST API.

How to use
==========

Deploy verticle as usual.

```
vertx.deployVerticle("textback.servicebus.SbApi", [
        "instances": 1, // number of vericles, usualy 1 is enough
        "config"   : [
                "AZURE_SB_LISTEN_QUEUE_NAME": ..., // name of Azure queue or subscription that should be listened by verticle
                "AZURE_SB_SEND_QUEUE_NAME"  : ..., // name of queue where to send outgoing messages
                "EB_RECEIVE_ADDRESS"        : ..., // vertx EventBus address what should be listened by verticle
                "EB_SEND_ADDRESS"           : ..., // vertx EventBus address where to send incoming messages
        ]
]) {
    if (it.failed()) {
      ... // action to execute if verticle failed to deploy
    } else {
      ...
    }
}
```

Verticle polls configured ServiceBus queue or subscription and when message arrives sends it to vertx EventBus. 
Also verticle listens configured address on vertx EventBus and when message arrives sends it to Azure ServiceBus.
