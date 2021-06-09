package me.lengyan.dapr.core.loader;

import com.google.common.collect.ImmutableList;

import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.ServiceLoader;

/**
 * Spi Service Registry
 * @author lengyan 2020/3/9 10:44 PM
 */
public class SpiServiceRegistry extends AbstractServiceRegistry {
    @Override
    protected <T> Collection<T> doLookupProviders(Class<T> clazz, ClassLoader classLoader) {
        Iterator<T> iterator = ServiceLoader.load(clazz, classLoader).iterator();
        if (iterator.hasNext()) {
            return ImmutableList.copyOf(iterator);
        } else {
            return Collections.emptyList();
        }
    }
}
