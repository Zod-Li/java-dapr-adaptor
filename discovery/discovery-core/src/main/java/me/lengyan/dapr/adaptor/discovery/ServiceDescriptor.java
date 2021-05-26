package me.lengyan.dapr.adaptor.discovery;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * 服务描述
 * @author boyu.lby
 */
public interface ServiceDescriptor {

    String getServiceName();

    String getVersion();

    /**
     * 不同rpc协议的接口签名, 如grpc为proto.v1.AService
     * @return
     */
    default String signature() {
        checkArgument(getServiceName() != null, "service name must not be empty!");
        checkArgument(getVersion() != null, "service version must not be empty!");
        return getServiceName() + ":" + getVersion();
    }

}
