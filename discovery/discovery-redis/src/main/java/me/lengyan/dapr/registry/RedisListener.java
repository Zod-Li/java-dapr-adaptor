package me.lengyan.dapr.registry;

@FunctionalInterface
public interface RedisListener {

    String REGISTER_EVENT_TYPE = "register";

    String UNREGISTER_EVENT_TYPE = "unregister";

    void onEvent(String event);

}
