package me.lengyan.dapr.grpc;

import com.google.protobuf.ByteString;
import com.google.protobuf.Descriptors;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.reflection.v1alpha.ServerReflectionGrpc;
import io.grpc.reflection.v1alpha.ServerReflectionRequest;
import io.grpc.reflection.v1alpha.ServerReflectionResponse;
import io.grpc.stub.StreamObserver;
import me.lengyan.dapr.core.Properties;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.ConstructorArgumentValues;

import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

import static com.google.common.base.Preconditions.checkArgument;
import static me.lengyan.dapr.grpc.utils.ReflectionUtils.getFileDescriptor;

public class GrpcServiceManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(GrpcServiceManager.class);

    // K - proto.ServiceName, TODO OOM protect
    private static final Map<String, CompletableFuture<Descriptors.ServiceDescriptor>> serviceDescriptors = new ConcurrentHashMap<>();

    public GrpcServiceManager() {
    }

    public static Descriptors.ServiceDescriptor findServiceDescriptor(String fullMethodName) throws Exception {
        checkArgument(StringUtils.isNotBlank(fullMethodName), "fullMethodName not valid");

        // reflect invoke by service
        CompletableFuture<Descriptors.ServiceDescriptor> future = serviceDescriptors.computeIfAbsent(
            extraPrefix(fullMethodName), (k) -> CompletableFuture.supplyAsync(() -> findDescriptor(fullMethodName)));

        // do something?
        return future.get(3000, TimeUnit.MILLISECONDS);
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
            .forAddress(Properties.MAIN_CONTAINER_IP.get(), Properties.GRPC_SERVER_PORT.get())
            .usePlaintext()
            .build();

        CountDownLatch latch = new CountDownLatch(1);
        Descriptors.ServiceDescriptor[] holder = new Descriptors.ServiceDescriptor[1];
        StreamObserver<ServerReflectionResponse> respObserver = new StreamObserver<ServerReflectionResponse>() {
            @Override
            public void onNext(ServerReflectionResponse resp) {
                // 文件类型响应
                if (resp.getMessageResponseCase() != ServerReflectionResponse.MessageResponseCase.FILE_DESCRIPTOR_RESPONSE) {
                    LOGGER.error("unexpect server reflection response type: {}", resp.getMessageResponseCase().name());
                    return;
                }
                List<ByteString> fdpList = resp.getFileDescriptorResponse().getFileDescriptorProtoList();
                try {
                    Descriptors.FileDescriptor fileDescriptor = getFileDescriptor(fdpList, packageName, serviceName);
                    Descriptors.ServiceDescriptor serviceDescriptor = fileDescriptor.findServiceByName(serviceName);
                    // Null is returnable
                    holder[0] = serviceDescriptor;
                } catch (Exception e) {
                    LOGGER.error(e.getMessage(), e);
                }
            }

            @Override
            public void onError(Throwable t) {
                LOGGER.error("dapr adaptor reflection invoke error");
                latch.countDown();
            }

            @Override
            public void onCompleted() {
                latch.countDown();
            }
        };

        StreamObserver<ServerReflectionRequest> reqObserver = ServerReflectionGrpc.newStub(reflectionChannel).serverReflectionInfo(respObserver);
        reqObserver.onNext(ServerReflectionRequest.newBuilder().setFileContainingSymbol(fullMethodName).build());
        //reqObserver.onCompleted();

        try {
            latch.await();
        } catch (InterruptedException e) {
        }
        return holder[0];
    }

    private static String extraPrefix(String content) {
        return content.substring(0, content.lastIndexOf("."));
    }

    private static String extraSuffix(String content) {
        return content.substring(content.lastIndexOf(".") + 1);
    }

}
