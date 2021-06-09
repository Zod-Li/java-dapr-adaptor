package me.lengyan.dapr.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Function;

/**
 * Important! : All config name(env name) has dapr.adaptor prefix
 * @author lengyan.lby
 */
public class Properties {

    private static final Logger LOGGER = LoggerFactory.getLogger(Properties.class);

    private static final String CONF_PREFIX = "dapr.adaptor.";
    private static final String                     ENV_PREFIX = "DAPR_ADAPTOR_";

    private static final int                        DEFAULT_GRPC_PORT = 6565;
    private static final int                        DEFAULT_REDIS_PORT = 6379;
    private static final String                     DEFAULT_DISCOVERY_REGISTRY_TYPE = "redis";

    // Parsers
    private static final Function<String, String>   STRING_CONFIG_PARSER = String::valueOf;
    private static final Function<String, Boolean>  BOOLEAN_CONFIG_PARSER = Boolean::valueOf;
    private static final Function<String, Integer>  INTEGER_CONFIG_PARSER = Integer::valueOf;

    // Properties
    public static final GenericProperty<Boolean>    DAPR_ENABLED;
    public static final GenericProperty<String>     DAPR_APP_ID;

    public static final GenericProperty<String>     MAIN_CONTAINER_IP;
    public static final GenericProperty<Integer>    GRPC_SERVER_PORT;

    // TODO 考虑挪到discovery-redis模块
    //public static final GenericProperty<String>     REDIS_SERVER_ADDR;
    public static final GenericProperty<String>     REDIS_HOST;
    public static final GenericProperty<Integer>    REDIS_PORT;
    public static final GenericProperty<String>     REDIS_PASSWORD;
    public static final GenericProperty<Integer>    REDIS_DB;

    public static final GenericProperty<Boolean>    REDIS_TEST_ON_BORROW;
    public static final GenericProperty<Boolean>    REDIS_TEST_ON_RETURN;
    public static final GenericProperty<Boolean>    REDIS_TEST_WHILE_IDLE;
    public static final GenericProperty<Integer>    REDIS_MAX_IDLE;
    public static final GenericProperty<Integer>    REDIS_MIN_IDLE;
    public static final GenericProperty<Integer>    REDIS_MAX_ACTIVE;
    public static final GenericProperty<Integer>    REDIS_MAX_TOTAL;
    public static final GenericProperty<Integer>    REDIS_TIMEOUT;
    public static final GenericProperty<Integer>    REDIS_MAX_WAIT;


    public static final GenericProperty<String>     DISCOVERY_TYPE;


    public Properties() {}

    static {
        DAPR_ENABLED = new GenericProperty<>("dapr.enabled", "DAPR_ENABLED", false, BOOLEAN_CONFIG_PARSER);
        DAPR_APP_ID = new GenericProperty<>("dapr.appId", "DAPR_APP_ID", null, STRING_CONFIG_PARSER);

        MAIN_CONTAINER_IP = new GenericProperty<>(CONF_PREFIX + "container.ip", ENV_PREFIX + "CONTAINER_IP", "127.0.0.1", STRING_CONFIG_PARSER);

        GRPC_SERVER_PORT = new GenericProperty<>(CONF_PREFIX + "grpc.server.port", ENV_PREFIX + "GRPC_SERVER_PORT", DEFAULT_GRPC_PORT, INTEGER_CONFIG_PARSER);

        // discovery-redis config
        //REDIS_SERVER_ADDR = new GenericProperty<>("redis.server.addr", "REDIS_SERVER_ADDR", "127.0.0.1:6379", STRING_CONFIG_PARSER);
        REDIS_HOST = new GenericProperty<>(CONF_PREFIX + "redis.host", ENV_PREFIX + "REDIS_HOST", "127.0.0.1", STRING_CONFIG_PARSER);
        REDIS_PORT = new GenericProperty<>(CONF_PREFIX + "redis.port", ENV_PREFIX + "REDIS_PORT", DEFAULT_REDIS_PORT, INTEGER_CONFIG_PARSER);
        REDIS_PASSWORD = new GenericProperty<>(CONF_PREFIX + "redis.password", ENV_PREFIX + "REDIS_PASSWORD", null, STRING_CONFIG_PARSER);
        REDIS_DB = new GenericProperty<>(CONF_PREFIX + "redis.database", ENV_PREFIX + "REDIS_DATABASE", 0, INTEGER_CONFIG_PARSER);
        REDIS_TEST_ON_BORROW = new GenericProperty<>(CONF_PREFIX + "redis.test.on.borrow", ENV_PREFIX + "REDIS_TEST_ON_BORROW", true, BOOLEAN_CONFIG_PARSER);
        REDIS_TEST_ON_RETURN = new GenericProperty<>(CONF_PREFIX + "redis.test.on.return", ENV_PREFIX + "REDIS_TEST_ON_RETURN", false, BOOLEAN_CONFIG_PARSER);
        REDIS_TEST_WHILE_IDLE = new GenericProperty<>(CONF_PREFIX + "redis.test.while.idle", ENV_PREFIX + "REDIS_TEST_WHILE_IDLE", false, BOOLEAN_CONFIG_PARSER);
        REDIS_MAX_IDLE = new GenericProperty<>(CONF_PREFIX + "redis.max.idle", ENV_PREFIX + "REDIS_MAX_IDLE", 0, INTEGER_CONFIG_PARSER);
        REDIS_MIN_IDLE = new GenericProperty<>(CONF_PREFIX + "redis.min.idle", ENV_PREFIX + "REDIS_MIN_IDLE", 0, INTEGER_CONFIG_PARSER);
        REDIS_MAX_ACTIVE = new GenericProperty<>(CONF_PREFIX + "redis.max.active", ENV_PREFIX + "REDIS_MAX_ACTIVE", 0, INTEGER_CONFIG_PARSER);
        REDIS_MAX_TOTAL = new GenericProperty<>(CONF_PREFIX + "redis.max.total", ENV_PREFIX + "REDIS_MAX_TOTAL", 0, INTEGER_CONFIG_PARSER);
        REDIS_TIMEOUT = new GenericProperty<>(CONF_PREFIX + "redis.timeout", ENV_PREFIX + "REDIS_TIMEOUT", 0, INTEGER_CONFIG_PARSER);
        REDIS_MAX_WAIT = new GenericProperty<>(CONF_PREFIX + "redis.max.wait", ENV_PREFIX + "REDIS_MAX_WAIT", REDIS_TIMEOUT.get(), INTEGER_CONFIG_PARSER);

        DISCOVERY_TYPE = new GenericProperty<>(CONF_PREFIX + "discovery.type", ENV_PREFIX + "DISCOVERY_TYPE", DEFAULT_DISCOVERY_REGISTRY_TYPE, STRING_CONFIG_PARSER);
    }

    /**
     * dapr enabled?
     * TODO k8s部署模式下需要从annotation获取, 但不想引入k8sClient
     */
    public static boolean daprEnabled() {
        return DAPR_ENABLED.get();
    }

    public static class GenericProperty<T> {

        private final String name;
        private final String envName;
        private final T defaultValue;
        private final Function<String, T> parser;

        GenericProperty(String name, String envName, T defaultValue, Function<String, T> parser) {
            this.name = name;
            this.envName = envName;
            this.defaultValue = defaultValue;
            this.parser = parser;
        }

        public T get() {
            String propValue = System.getProperty(this.name);
            if (propValue != null && !propValue.trim().isEmpty()) {
                try {
                    return parser.apply(propValue);
                } catch (IllegalArgumentException var5) {
                    LOGGER.warn(String.format("Invalid value in property: %s", this.name));
                }
            }

            String envValue = System.getenv(this.envName);
            if (envValue != null && !envValue.trim().isEmpty()) {
                try {
                    return parser.apply(envValue);
                } catch (IllegalArgumentException var4) {
                    LOGGER.warn(String.format("Invalid value in environment variable: %s", this.envName));
                }
            }

            return this.defaultValue;
        }
    }

}
