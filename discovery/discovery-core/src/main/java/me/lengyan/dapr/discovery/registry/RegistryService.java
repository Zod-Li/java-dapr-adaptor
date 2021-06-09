package me.lengyan.dapr.discovery.registry;

import java.util.List;

/**
 *
 * @param <T> listener
 */
public interface RegistryService<T>{

    /**
     * GRPC  - proto.v1.GrpcService : {dapr-app-id}
     * DUBBO - com.xxx.xxx.AService:1.0.0 : {dapr-app-id}
     * HSF   - com.xxx.xxx.AService:1.0.0 : {dapr-app-id}
     * @throws Exception
     */
    void register(ServiceDescriptor descriptor, String appId) throws Exception;

    void unregister(ServiceDescriptor descriptor, String appId) throws Exception;

    void subscribe(String serviceName, T listener) throws Exception;

    void unsubscribe() throws Exception;

    /**
     *
     * @param serviceDescriptor
     * @return appIds
     * @throws Exception
     */
    List<String> lookup(ServiceDescriptor serviceDescriptor) throws Exception;

    void close() throws Exception;

}
