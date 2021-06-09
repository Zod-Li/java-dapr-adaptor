package me.lengyan.dapr.registry;

import me.lengyan.dapr.core.DefaultThreadFactory;
import me.lengyan.dapr.core.Properties;
import me.lengyan.dapr.core.util.StringGenerator;
import me.lengyan.dapr.discovery.registry.RegistryService;
import me.lengyan.dapr.discovery.registry.ServiceDescriptor;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPubSub;
import redis.clients.jedis.Protocol;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.stream.Collectors;

import static com.google.common.base.Preconditions.checkArgument;
import static java.lang.String.format;
import static me.lengyan.dapr.core.Properties.*;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

/**
 *
 * @author lengyan.lby
 */
public class RedisRegistryServiceImpl implements RegistryService<RedisListener> {

    private static final Logger LOGGER = LoggerFactory.getLogger(RedisRegistryServiceImpl.class);
    private static final String EVENT_TYPE_SPLITTER = "-";
    /**
     * Node UUID
     */
    private static final String UUID = StringGenerator.generateWithUppercase(8);

    private static final ConcurrentHashMap<String, List<RedisListener>> SERVICE_LISTENER_MAP = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Set<String>> SERVICE_MAP = new ConcurrentHashMap<>();

    private static volatile RedisRegistryServiceImpl instance;
    private static volatile JedisPool jedisPool;

    //private ExecutorService threadPoolExecutor = new ScheduledThreadPoolExecutor(1,
    //    new DefaultThreadFactory("RedisRegistryService"));
    private ExecutorService threadPoolExecutor = Executors.newFixedThreadPool(1);

    public RedisRegistryServiceImpl() {
        GenericObjectPoolConfig redisConfig = new GenericObjectPoolConfig();
        redisConfig.setTestOnBorrow(REDIS_TEST_ON_BORROW.get());
        redisConfig.setTestOnReturn(REDIS_TEST_ON_RETURN.get());
        redisConfig.setTestWhileIdle(REDIS_TEST_WHILE_IDLE.get());
        if (REDIS_MAX_IDLE.get() > 0) {
            redisConfig.setMaxIdle(REDIS_MAX_IDLE.get());
        }
        if (REDIS_MIN_IDLE.get() > 0) {
            redisConfig.setMinIdle(REDIS_MIN_IDLE.get());
        }
        if (REDIS_MAX_ACTIVE.get() > 0) {
            redisConfig.setMaxTotal(REDIS_MAX_ACTIVE.get());
        }
        if (REDIS_MAX_TOTAL.get() > 0) {
            redisConfig.setMaxTotal(REDIS_MAX_TOTAL.get());
        }
        if (REDIS_MAX_WAIT.get() > 0) {
            redisConfig.setMaxWaitMillis(REDIS_MAX_WAIT.get());
        }
        jedisPool = new JedisPool(redisConfig, REDIS_HOST.get(), REDIS_PORT.get(), Protocol.DEFAULT_TIMEOUT, REDIS_PASSWORD.get(), REDIS_DB.get());
    }

    static RedisRegistryServiceImpl getInstance() {
        if (instance == null) {
            synchronized (RedisRegistryServiceImpl.class) {
                if (instance == null) {
                    instance = new RedisRegistryServiceImpl();
                }
            }
        }
        return instance;
    }

    // K - 服务名&类型
    // filed - url / dapr-app-id   dapr://ip:port:appid
    // value - expire time / ip
    @Override
    public void register(ServiceDescriptor serviceDescriptor, String appId) throws Exception {
        checkArgument(isNotBlank(appId), "appId can not be empty");
        final String serviceKey = serviceDescriptor.signature();
        final String uniqueAppId = getNodeUniqueAppId(appId);
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.hset(getDiscoveryKey(serviceKey), uniqueAppId, Properties.MAIN_CONTAINER_IP.get());
            jedis.publish(getChannelKey(serviceKey), onChangeEventMsg(RedisListener.REGISTER_EVENT_TYPE, uniqueAppId));
        }
    }

    private String getNodeUniqueAppId(String appId) {
        // TODO 先尝试获取POD_IP/LOCAL_ADDRESS 如果不符合要求(127.0.0.1/0.0.0.0), 则使用短8位UUID
        return appId + EVENT_TYPE_SPLITTER + UUID;
    }

    private String onChangeEventMsg(String type, String msg) {
        checkArgument(isNotBlank(msg), "msg can not be empty");
        return format("%s"+ EVENT_TYPE_SPLITTER +"%s", msg, type);
    }

    private String getDiscoveryKey(String serviceName) {
        return MetaStorageKeys.SERVICE_KEY.format(serviceName);
    }

    private String getChannelKey(String serviceName) {
        return MetaStorageKeys.CHANNEL_KEY.format(serviceName);
    }

    @Override
    public void unregister(ServiceDescriptor serviceDescriptor, String appId) throws Exception {
        checkArgument(isNotBlank(appId), "appId can not be empty");
        final String serviceName = serviceDescriptor.signature();
        final String uniqueAppId = getNodeUniqueAppId(appId);
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.hdel(getDiscoveryKey(serviceName), uniqueAppId);
            jedis.publish(getChannelKey(serviceName), onChangeEventMsg(RedisListener.UNREGISTER_EVENT_TYPE, uniqueAppId));
        }
    }

    @Override
    public void subscribe(final String serviceName, RedisListener listener) throws Exception {
        SERVICE_LISTENER_MAP.computeIfAbsent(serviceName, key -> new ArrayList<>()).add(listener);
        threadPoolExecutor.submit(() -> {
            try {
                try (Jedis jedis = jedisPool.getResource()) {
                    jedis.subscribe(new PubSub(SERVICE_LISTENER_MAP.get(serviceName)), getChannelKey(serviceName));
                }
            } catch (Exception e) {
                LOGGER.error(e.getMessage(), e);
            }
        });
    }

    @Override
    public void unsubscribe() throws Exception {
    }

    @Override
    public List<String> lookup(ServiceDescriptor serviceDescriptor) throws Exception {
        final String serviceName = serviceDescriptor.signature();
        if (!SERVICE_LISTENER_MAP.containsKey(serviceName)) {
            // do init
            Map<String, String> fields;
            try (Jedis jedis = jedisPool.getResource()) {
                fields = jedis.hgetAll(getDiscoveryKey(serviceName));
            }
            Set<String> appIds = fields.keySet().stream().map(this::resolveAppId).collect(Collectors.toSet());
            SERVICE_MAP.put(serviceName, appIds);

            listen(serviceName);
        }
        return new ArrayList<>(SERVICE_MAP.getOrDefault(serviceName, Collections.emptySet()));
    }

    private String resolveAppId(String value) {
        int idx = value.lastIndexOf(EVENT_TYPE_SPLITTER);
        if (idx == -1) {
            return value;
        }
        return value.substring(0, idx);
    }

    private void listen(final String serviceName) throws Exception {
        this.subscribe(serviceName, msg -> {
            int idx = msg.lastIndexOf(EVENT_TYPE_SPLITTER);
            final String eventType = msg.substring(idx + 1);
            final String appId = resolveAppId(msg.substring(0, idx));
            switch (eventType) {
                case RedisListener.REGISTER_EVENT_TYPE:
                    SERVICE_MAP.get(serviceName).add(appId);
                    break;
                case RedisListener.UNREGISTER_EVENT_TYPE:
                    SERVICE_MAP.get(serviceName).remove(appId);
                    break;
                default:
                    throw new RuntimeException("unknown msg:" + msg);
            }
        });
    }

    private static class PubSub extends JedisPubSub {
        private final List<RedisListener> redisListeners;

        PubSub(List<RedisListener> redisListeners) {
            this.redisListeners = redisListeners;
        }

        @Override
        public void onMessage(String key, String msg) {
            for (RedisListener listener : redisListeners) {
                listener.onEvent(msg);
            }
        }
    }

    @Override
    public void close() throws Exception {
        jedisPool.destroy();
    }
}
