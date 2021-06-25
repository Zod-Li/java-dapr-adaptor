package me.lengyan.dapr.registry;

import me.lengyan.dapr.core.loader.LoadLevel;
import me.lengyan.dapr.discovery.registry.RegistryProvider;
import me.lengyan.dapr.discovery.registry.RegistryService;

@LoadLevel(name = "redis", order = 1)
public class RedisRegistryProvider implements RegistryProvider {
    @Override
    public RegistryService provide() {
        return RedisRegistryServiceImpl.getInstance();
    }
}
