## anr 监控
- 在子线程中写个轮训，每次向主Handler中丢个消息，然后几秒钟后检查该消息是否已经消费。
    - 如果已经消费，则说明正常
    - 如果还未消费，给了几秒钟还没处理消息，那就是有问题，有可能有ANR.
- 可以从 AMS 中获取关于ANR的信息




