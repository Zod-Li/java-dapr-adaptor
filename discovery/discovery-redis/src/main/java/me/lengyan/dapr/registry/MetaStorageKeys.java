package me.lengyan.dapr.registry;

public enum MetaStorageKeys {

    SERVICE_KEY("KEY:%s"),

    CHANNEL_KEY("CHANNEL:%s");

    private String pattern;
    MetaStorageKeys(String pattern) {
        this.pattern = pattern;
    }

    public String format(String... args) {
        return String.format(pattern, args);
    }

}
