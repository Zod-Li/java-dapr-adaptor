package me.lengyan.dapr.discovery.registry;

import me.lengyan.dapr.core.Properties;
import me.lengyan.dapr.core.loader.LoadLevel;
import me.lengyan.dapr.core.loader.SpiServiceRegistry;

import java.util.Collection;

public class RegistryFactory {

    private static RegistryService instance = null;

    public static RegistryService getInstance() {
        if (instance == null) {
            synchronized (RegistryFactory.class) {
                if (instance == null) {
                    instance = build();
                }
            }
        }
        return instance;
    }

    private static RegistryService build() {
        final String type = Properties.DISCOVERY_TYPE.get();
        Collection<RegistryProvider> providers = new SpiServiceRegistry().lookupProviders(RegistryProvider.class, RegistryFactory.class.getClassLoader());
        if (providers.isEmpty()) {
            throw new IllegalStateException("No provider found for class " + RegistryProvider.class.getName());
        }
        RegistryProvider provider = providers.stream()
            .filter(item -> {
                LoadLevel loadLevel = item.getClass().getAnnotation(LoadLevel.class);
                return loadLevel.name().equalsIgnoreCase(type);
            })
            .findAny()
            .orElseThrow(() -> new IllegalStateException(
                "No '" + type + "' type of provider found for class " + RegistryProvider.class.getName()));
        return provider.provide();
    }


}
