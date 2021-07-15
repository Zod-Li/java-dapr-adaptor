package me.lengyan.dapr.springboot;

import io.dapr.v1.AppCallbackGrpc;
import io.grpc.BindableService;
import io.grpc.ServerServiceDefinition;
import me.lengyan.dapr.core.Properties;
import me.lengyan.dapr.discovery.registry.RegistryFactory;
import me.lengyan.dapr.discovery.registry.ServiceDescriptor;
import me.lengyan.dapr.grpc.GrpcProtocolDescriptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.support.AbstractApplicationContext;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public class DaprGrpcAdaptorRunner implements CommandLineRunner, DisposableBean {

    private static final Logger LOGGER = LoggerFactory.getLogger(DaprGrpcAdaptorRunner.class);

    private static final List<ServiceDescriptor> GRPC_SERVICE_PROVIDERS = new ArrayList<>();

    @Autowired
    private AbstractApplicationContext applicationContext;

    @Override
    public void run(String... args) throws Exception {
        // discovery grpc service
        Stream.of(applicationContext.getBeanNamesForType(BindableService.class))
            .forEach(name -> {
                BindableService bindableService = applicationContext.getBeanFactory().getBean(name, BindableService.class);

                ServerServiceDefinition definition = bindableService.bindService();
                String serviceName = definition.getServiceDescriptor().getName();
                Object schemeDesc = definition.getServiceDescriptor().getSchemaDescriptor();

                // 忽略AppCallbackGrpc.SERVICE_NAME服务避免套娃
                if (AppCallbackGrpc.SERVICE_NAME.equals(serviceName)) {
                    return;
                }

                LOGGER.info("dapr-adaptor found grpc service name: {}", serviceName);
                // TODO cache grpc Descriptors.FileDescriptor
                // TODO 注册grpc服务, 应该等sidecar ready后再注册
                try {
                    ServiceDescriptor descriptor = new GrpcProtocolDescriptor(serviceName);
                    RegistryFactory.getInstance().register(descriptor, Properties.DAPR_APP_ID.get());
                    GRPC_SERVICE_PROVIDERS.add(descriptor);
                } catch (Exception e) {
                    LOGGER.error("register service failed: " + serviceName, e);
                }
            });
    }

    @Override
    public void destroy() throws Exception {
        GRPC_SERVICE_PROVIDERS.forEach(service -> {
            try {
                RegistryFactory.getInstance().unregister(service, Properties.DAPR_APP_ID.get());
            } catch (Exception e) {
                LOGGER.error(e.getMessage(), e);
            }
        });

    }
}
