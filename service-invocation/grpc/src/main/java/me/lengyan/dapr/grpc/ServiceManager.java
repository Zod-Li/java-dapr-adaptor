package me.lengyan.dapr.grpc;

import com.google.protobuf.ByteString;
import com.google.protobuf.Descriptors;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.reflection.v1alpha.ServerReflectionGrpc;
import io.grpc.reflection.v1alpha.ServerReflectionRequest;
import io.grpc.reflection.v1alpha.ServerReflectionResponse;
import io.grpc.stub.StreamObserver;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

import static com.google.common.base.Preconditions.checkArgument;
import static me.lengyan.dapr.core.Properties.*;
import static me.lengyan.dapr.grpc.utils.ReflectionUtils.getFileDescriptor;

public class ServiceManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(ServiceManager.class);

    // K - proto.ServiceName, TODO OOM protect
    private static final Map<String, CompletableFuture<Descriptors.ServiceDescriptor>> serviceDescriptors = new ConcurrentHashMap<>();

    public ServiceManager() {
    }

    public static Descriptors.ServiceDescriptor findServiceDescriptor(String fullMethodName) throws Exception {
        checkArgument(StringUtils.isNotBlank(fullMethodName), "fullMethodName not valid");
        // reflect invoke by service
        CompletableFuture<Descriptors.ServiceDescriptor> future = serviceDescriptors.computeIfAbsent(
            extraPrefix(fullMethodName), (k) -> CompletableFuture.supplyAsync(() -> findDescriptor(fullMethodName)));
        return future.get();
    }

    /**
     *
     * @param fullMethodName
     * @return
     */
    public static Descriptors.ServiceDescriptor findDescriptor(String fullMethodName) {
        checkArgument(StringUtils.isNotBlank(fullMethodName), "fullMethodName not valid");

        final String fullServiceName = extraPrefix(fullMethodName);
        final String packageName = extraPrefix(fullServiceName);
        final String serviceName = extraSuffix(fullServiceName);

        ManagedChannel reflectionChannel = ManagedChannelBuilder
            .forAddress(MAIN_CONTAINER_IP.get(), GRPC_SERVER_PORT.get())
            .usePlaintext()
            .build();
        try {
            LOGGER.info("build reflection descriptor: " + reflectionChannel.toString());
            CountDownLatch latch = new CountDownLatch(1);
            Descriptors.ServiceDescriptor[] holder = new Descriptors.ServiceDescriptor[1];
            StreamObserver<ServerReflectionResponse> respObserver = new StreamObserver<ServerReflectionResponse>() {
                @Override
                public void onNext(ServerReflectionResponse resp) {
                    // 文件类型响应
                    if (resp.getMessageResponseCase()
                        != ServerReflectionResponse.MessageResponseCase.FILE_DESCRIPTOR_RESPONSE) {
                        LOGGER.error("unexpect server reflection response type: {}",
                            resp.getMessageResponseCase().name());
                        latch.countDown();
                        return;
                    }
                    List<ByteString> fdpList = resp.getFileDescriptorResponse().getFileDescriptorProtoList();
                    try {
                        Descriptors.FileDescriptor fileDescriptor = getFileDescriptor(fdpList, packageName,
                            serviceName);
                        Descriptors.ServiceDescriptor serviceDescriptor = fileDescriptor.findServiceByName(serviceName);
                        // null is returnable
                        holder[0] = serviceDescriptor;
                    } catch (Exception e) {
                        LOGGER.error(e.getMessage(), e);
                    } finally {
                        latch.countDown();
                    }
                }

                @Override
                public void onError(Throwable t) {
                    LOGGER.error("dapr adaptor reflection invoke error");
                }

                @Override
                public void onCompleted() {
                }
            };
            StreamObserver<ServerReflectionRequest> reqObserver = ServerReflectionGrpc.newStub(reflectionChannel)
                .serverReflectionInfo(respObserver);
            reqObserver.onNext(ServerReflectionRequest.newBuilder().setFileContainingSymbol(fullMethodName).build());

            try {
                latch.await(3000, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
            }
            return holder[0];
        } finally {
            reflectionChannel.shutdown();
        }
    }

    private static String extraPrefix(String content) {
        return content.substring(0, content.lastIndexOf("."));
    }

    private static String extraSuffix(String content) {
        return content.substring(content.lastIndexOf(".") + 1);
    }

}
