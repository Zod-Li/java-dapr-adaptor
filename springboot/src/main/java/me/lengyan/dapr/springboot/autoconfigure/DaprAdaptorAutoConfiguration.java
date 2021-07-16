package me.lengyan.dapr.springboot.autoconfigure;

import me.lengyan.dapr.grpc.DaprCallbackService;
import me.lengyan.dapr.grpc.ServiceManager;
import me.lengyan.dapr.springboot.DaprGrpcAdaptorRunner;
import org.springframework.boot.autoconfigure.AutoConfigureOrder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@AutoConfigureOrder
@Configuration
@ConditionalOnWebApplication
public class DaprAdaptorAutoConfiguration {

    private static final String SWITCH_PROPERTY = "dapr.enabled";

    @Bean
    @ConditionalOnProperty(value = SWITCH_PROPERTY, havingValue = "true")
    public DaprGrpcAdaptorRunner grpcAdaptorRunner() {
        return new DaprGrpcAdaptorRunner();
    }

    @Bean
    public ServiceManager grpcServiceManager() {
        return new ServiceManager();
    }

    @Bean
    //@ConditionalOnBean(DaprGrpcAdaptorRunner.class)
    public DaprCallbackService daprCallbackService() {
        return new DaprCallbackService();
    }

    // TODO add GrpcClientInterceptor,  starter非官方, 客户端使用方式不确定, 如何自动配置? 还是让用户自己写SPI services文件?

}
