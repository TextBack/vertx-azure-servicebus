vertx-azure-servicebus
======================

Consumer and producer for Azure ServiceBus. Written from scratch using Azure REST API.

How to use
==========

Deploy verticle as usual.

Verticle polls configured ServiceBus queue or subscription and when message arrives sends it to vertx EventBus. 
Also verticle listens configured address on vertx EventBus and when message arrives sends it to Azure ServiceBus.
