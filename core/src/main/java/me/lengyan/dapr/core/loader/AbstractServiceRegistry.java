package me.lengyan.dapr.core.loader;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static java.lang.String.format;
import static org.apache.commons.lang3.StringUtils.join;

/**
 *
 * @author lengyan 2020/3/9 7:54 PM
 */
public abstract class AbstractServiceRegistry implements ServiceRegistry {

    @Override
    public <T> Collection<T> lookupProviders(Class<T> clazz, ClassLoader classLoader) {
        classLoader = Objects.isNull(classLoader) ? Thread.currentThread().getContextClassLoader() : classLoader;
        return this.doLookupProviders(clazz, classLoader);
    }

    protected abstract <T> Collection<T> doLookupProviders(Class<T> clazz, ClassLoader classLoader);

    @Override
    public final <T> T lookupProvider(Class<T> providerClass, ClassLoader classLoader) {
        Collection<T> providers = lookupProviders(providerClass, classLoader);
        if (providers.isEmpty()) {
            throw new IllegalStateException("No provider found for class " + providerClass.getName());
        }
        if (providers.size() > 1) {
            List<String> providersNames =
                providers.stream().map(provider -> provider.getClass().getName()).collect(Collectors.toList());
            throw new IllegalStateException(format("More than one provided found for class %s, providers found are: %s",
                providerClass.getName(), join(providersNames, ",")));
        }
        return providers.iterator().next();
    }

}
