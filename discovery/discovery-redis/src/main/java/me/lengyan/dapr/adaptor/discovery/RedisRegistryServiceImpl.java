package me.lengyan.dapr.adaptor.discovery;

import me.lengyan.dapr.adaptor.discovery.registry.RegistryService;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.Protocol;

import java.util.List;

public class RedisRegistryServiceImpl implements RegistryService<RedisListener> {

    private static volatile RedisRegistryServiceImpl instance;
    private static volatile JedisPool jedisPool;

    public RedisRegistryServiceImpl() {

        String host = "";
        int port = 0;
        int db = 0;
        GenericObjectPoolConfig redisConfig = new GenericObjectPoolConfig();
        String password = null;
        if ("".equals(password)) {
            // 赋值paasword
            password = "xxx";
        }

        jedisPool = new JedisPool(redisConfig, host, port, Protocol.DEFAULT_TIMEOUT, password, db);

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

    @Override
    public void register(ServiceDescriptor serviceDescriptor, String appId) throws Exception {
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.hgetAll(serviceDescriptor.signature());
        }
        serviceDescriptor.signature();
    }

    @Override
    public void unregister(ServiceDescriptor serviceDescriptor) throws Exception {

    }

    @Override
    public void subscribe(RedisListener listener) throws Exception {

    }

    @Override
    public void unsubscribe() throws Exception {

    }

    @Override
    public List<String> lookup(ServiceDescriptor serviceDescriptor) throws Exception {


        return null;
    }

    @Override
    public void close() throws Exception {

    }
}
