package me.lengyan.dapr.adaptor.discovery;

import me.lengyan.dapr.adaptor.discovery.registry.RegistryProvider;
import me.lengyan.dapr.adaptor.discovery.registry.RegistryService;

public class RedisRegistryProvider implements RegistryProvider {

    @Override
    public RegistryService provide() {
        return RedisRegistryServiceImpl.getInstance();
    }

}
