package me.lengyan.dapr.adaptor.discovery.registry;

import me.lengyan.dapr.adaptor.discovery.ServiceDescriptor;

import java.util.List;

/**
 *
 * @param <T> listener
 */
public interface RegistryService<T>{

    /**
     * proto.v1.GrpcService : {dapr-app-id}
     * @throws Exception
     */
    void register(ServiceDescriptor descriptor, String appId) throws Exception;

    void unregister(ServiceDescriptor descriptor) throws Exception;

    void subscribe(T listener) throws Exception;

    void unsubscribe() throws Exception;

    List<String> lookup(ServiceDescriptor serviceDescriptor) throws Exception;

    void close() throws Exception;

}
