package me.lengyan.dapr.grpc;

import io.grpc.*;
import me.lengyan.dapr.discovery.registry.RegistryFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import static me.lengyan.dapr.core.Properties.daprEnabled;

/**
 * Dapr Grpc Client Interceptor
 * @author lengyan.lby
 */
public class DaprClientInterceptor implements ClientInterceptor {

    private static final Logger LOGGER = LoggerFactory.getLogger(DaprClientInterceptor.class);

    public DaprClientInterceptor() {
    }

    @Override
    public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(MethodDescriptor<ReqT, RespT> methodDescriptor,
                                                               CallOptions callOptions, Channel next) {
        ClientCall<ReqT, RespT> clientCall = next.newCall(methodDescriptor, callOptions);
        if (daprEnabled() && canProxied(methodDescriptor)) {
            // forward to dapr runtime
            String appId = select(listProviders(methodDescriptor.getServiceName()));
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("forward grpc call to dapr: {}|{}", appId, methodDescriptor.getFullMethodName());
            }
            return new DaprClientCall<>(appId, methodDescriptor, clientCall);
        }
        return clientCall;
    }

    private <ReqT, RespT> boolean canProxied(MethodDescriptor<ReqT, RespT> methodDescriptor) {
        List<String> appIds = listProviders(methodDescriptor.getServiceName());
        return appIds.size() > 0;
    }

    @SuppressWarnings("all")
    private List<String> listProviders(String serviceName) {
        try {
            return RegistryFactory.getInstance().lookup(new GrpcProtocolDescriptor(serviceName));
        } catch (Exception e) {
            LOGGER.error(e.getMessage(), e);
        }
        return Collections.emptyList();
    }

    // random select, no session
    private String select(List<String> appIds) {
        if (appIds == null || appIds.isEmpty()) {
            return null;
        }
        if (appIds.size() == 1) {
            return appIds.get(0);
        }
        int length = appIds.size();
        return appIds.get(ThreadLocalRandom.current().nextInt(length));
    }

}