package me.lengyan.dapr.core.loader;

import java.util.Collection;

/**
 * @author lengyan 2020/3/9 7:52 PM
 */
public interface ServiceRegistry {

    /**
     * lookup provider classes
     * @param clazz
     * @param classLoader
     * @param <T>
     */
    <T> Collection<T> lookupProviders(Class<T> clazz, ClassLoader classLoader);

    <T> T lookupProvider(Class<T> providerClass, ClassLoader loader);
}
