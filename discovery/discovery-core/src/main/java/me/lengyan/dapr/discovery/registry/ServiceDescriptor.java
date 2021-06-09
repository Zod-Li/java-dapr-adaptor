package me.lengyan.dapr.discovery.registry;

import com.google.common.base.Strings;

import static com.google.common.base.Preconditions.checkArgument;
import static java.lang.String.format;

/**
 * @author boyu.lby
 */
public interface ServiceDescriptor {

    String getProtocol();

    String getServiceName();

    String getVersion();

    /**
     * 接口签名, 如grpc为proto.v1.AService
     * @return
     */
    default String signature() {
        checkArgument(getProtocol() != null, "protocol can not be empty");
        checkArgument(getServiceName() != null, "serviceName can not be empty");
        if (Strings.isNullOrEmpty(getVersion())) {
            return format("%s/%s", getProtocol(), getServiceName());
        }
        return format("%s/%s:%s", getProtocol(), getServiceName(), getVersion());
    }

}
