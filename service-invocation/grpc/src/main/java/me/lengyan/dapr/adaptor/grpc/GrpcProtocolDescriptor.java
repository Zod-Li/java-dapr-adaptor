package me.lengyan.dapr.adaptor.grpc;

import me.lengyan.dapr.adaptor.discovery.ServiceDescriptor;

public class GrpcProtocolDescriptor implements ServiceDescriptor {

    private String proto;
    private String version;

    @Override
    public String getServiceName() {
        return this.proto;
    }

    @Override
    public String getVersion() {
        return this.version;
    }
}
