package me.lengyan.dapr.registry;

import me.lengyan.dapr.core.loader.LoadLevel;
import me.lengyan.dapr.discovery.registry.RegistryProvider;
import me.lengyan.dapr.discovery.registry.RegistryService;

@LoadLevel(name = "zookeeper", order = 1)
public class ZkRegistry implements RegistryProvider {
    @Override
    public RegistryService provide() {
        return null;
    }
}
