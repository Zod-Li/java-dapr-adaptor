package me.lengyan.dapr.discovery.registry;

/**
 * 注册中心类型枚举
 * @author boyu.lby
 */
public enum RegistryType {

    REDIS,
    ZOOKEEPER,
    ETCD;

    public RegistryType fromType(String name) {
        for (RegistryType registryType : RegistryType.values() ) {
            if (registryType.name().equalsIgnoreCase(name)) {
                return registryType;
            }
        }
        throw new IllegalArgumentException("not support registry type: " + name);
    }

}
