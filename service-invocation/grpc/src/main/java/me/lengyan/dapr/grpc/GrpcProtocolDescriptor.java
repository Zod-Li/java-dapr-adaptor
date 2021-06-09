package me.lengyan.dapr.grpc;

import me.lengyan.dapr.discovery.registry.ServiceDescriptor;

public class GrpcProtocolDescriptor implements ServiceDescriptor {

    public static final String GRPC_PROTOCOL = "grpc";

    private final String serviceName;

    public GrpcProtocolDescriptor(String serviceName) {
        this.serviceName = serviceName;
    }

    @Override
    public String getProtocol() {
        return GRPC_PROTOCOL;
    }

    @Override
    public String getServiceName() {
        return this.serviceName;
    }

    @Override
    public String getVersion() {
        return null;
    }
}
