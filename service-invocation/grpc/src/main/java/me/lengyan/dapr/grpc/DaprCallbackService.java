package me.lengyan.dapr.grpc;

import com.google.gson.Gson;
import com.google.protobuf.*;
import com.google.protobuf.util.JsonFormat;
import io.dapr.v1.AppCallbackGrpc;
import io.dapr.v1.CommonProtos;
import io.dapr.v1.DaprAppCallbackProtos;
import io.grpc.*;
import io.grpc.protobuf.ProtoUtils;
import io.grpc.reflection.v1alpha.ServerReflectionGrpc;
import io.grpc.reflection.v1alpha.ServerReflectionRequest;
import io.grpc.reflection.v1alpha.ServerReflectionResponse;
import io.grpc.stub.ClientCalls;
import io.grpc.stub.StreamObserver;
import me.lengyan.dapr.core.Properties;
//import net.devh.boot.grpc.server.service.GrpcService;
import org.lognet.springboot.grpc.GRpcService;
import org.slf4j.Logger;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static org.slf4j.LoggerFactory.getLogger;

/**
 * dapr callback adaptor, provide generic-call local grpc service
 * TODO
 *  1. memorize reflection protocols descriptor & init on bootstrap
 *  2. support server stream call
 * @author lengyan.lby
 */
// auto detect for lognet:grpc-spring-boot-starter
@GRpcService
// auto detect for net.devh:grpc-server-spring-boot-starter
//@GrpcService
public class DaprCallbackService extends AppCallbackGrpc.AppCallbackImplBase implements BindableService {

    private static final Logger LOGGER = getLogger(DaprCallbackService.class);

    private String grpcIp = Properties.MAIN_CONTAINER_IP.get();
    private int port = Properties.GRPC_SERVER_PORT.get();

    @Override
    public void onInvoke(CommonProtos.InvokeRequest request,
                         StreamObserver<CommonProtos.InvokeResponse> responseObserver) {
        //request.getMethod()
        LOGGER.info("dapr grpc callback, invoke method: " + new Gson().toJson(request));

        // TODO 这里最好是fullMethodName, 方便定位具体的Protocol和泛化调用, eg: proto.UserGrpcService/getUserById
        String fullMethodName = request.getMethod().replaceAll("/", ".");

        /**
         * 1. 构建泛化调用stub
         * 2. 根据泛化请求方法名获得proto描述文件
         * 3. 处理响应
         * 4. 执行调用
         */
        ManagedChannel reflectionChannel = ManagedChannelBuilder.forAddress(grpcIp, port).usePlaintext().build();

        ServerReflectionGrpc.ServerReflectionStub stub = ServerReflectionGrpc.newStub(reflectionChannel);

        StreamObserver<ServerReflectionResponse> respObserver = new StreamObserver<ServerReflectionResponse>() {
            @Override
            public void onNext(ServerReflectionResponse resp) {
                // 文件类型响应
                if (resp.getMessageResponseCase() != ServerReflectionResponse.MessageResponseCase.FILE_DESCRIPTOR_RESPONSE) {
                    responseObserver.onError(new DaprAdaptorException("service not found"));
                    return;
                }
                List<ByteString> fdpList = resp.getFileDescriptorResponse().getFileDescriptorProtoList();
                try {
                    String fullServiceName = extraPrefix(fullMethodName);
                    String methodName = extraSuffix(fullMethodName);
                    String packageName = extraPrefix(fullServiceName);
                    String serviceName = extraSuffix(fullServiceName);

                    Descriptors.FileDescriptor fileDescriptor = getFileDescriptor(fdpList, packageName, serviceName);
                    Descriptors.ServiceDescriptor serviceDescriptor = fileDescriptor.getFile().findServiceByName(
                        serviceName);
                    Descriptors.MethodDescriptor methodDescriptor = serviceDescriptor.findMethodByName(methodName);

                    // 发起请求
                    DynamicMessage result = doInvoke(reflectionChannel, fileDescriptor, methodDescriptor, request.getData());

                    // response to dapr
                    CommonProtos.InvokeResponse.Builder daprRespBuilder = CommonProtos.InvokeResponse.newBuilder();
                    daprRespBuilder.setData(Any.pack(result));
                    responseObserver.onNext(daprRespBuilder.build());
                    responseObserver.onCompleted();

                } catch (Exception e) {
                    LOGGER.error(e.getMessage(), e);
                    responseObserver.onError(e);
                }
            }

            @Override
            public void onError(Throwable t) {
                LOGGER.error("dapr adaptor reflection invoke error");
                responseObserver.onError(new DaprAdaptorException("dapr adaptor reflection invoke error", t));
            }

            @Override
            public void onCompleted() {}
        };

        StreamObserver<ServerReflectionRequest> reqObserver = stub.serverReflectionInfo(respObserver);
        ServerReflectionRequest reflectionRequest = ServerReflectionRequest.newBuilder()
            .setFileContainingSymbol(fullMethodName)
            .build();
        reqObserver.onNext(reflectionRequest);
    }

    private static String extraPrefix(String content) {
        return content.substring(0, content.lastIndexOf("."));
    }

    private static String extraSuffix(String content) {
        return content.substring(content.lastIndexOf(".") + 1);
    }

    private static Descriptors.FileDescriptor getFileDescriptor(List<ByteString> fileDescriptorProtoList,
                                                                String packageName,
                                                                String serviceName) throws Exception {

        Map<String, DescriptorProtos.FileDescriptorProto> fileDescriptorProtoMap =
            fileDescriptorProtoList.stream()
                .map(bs -> {
                    try {
                        return DescriptorProtos.FileDescriptorProto.parseFrom(bs);
                    } catch (InvalidProtocolBufferException e) {
                        e.printStackTrace();
                    }
                    return null;
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(DescriptorProtos.FileDescriptorProto::getName, f -> f));


        if (fileDescriptorProtoMap.isEmpty()) {
            LOGGER.error("service not found");
            throw new IllegalArgumentException("service not found");
        }

        // 查找服务对应的 Proto 描述
        DescriptorProtos.FileDescriptorProto fileDescriptorProto = findServiceFileDescriptorProto(packageName, serviceName, fileDescriptorProtoMap);

        // 获取这个 Proto 的依赖
        Descriptors.FileDescriptor[] dependencies = getDependencies(fileDescriptorProto, fileDescriptorProtoMap);

        // 生成 Proto 的 FileDescriptor
        return Descriptors.FileDescriptor.buildFrom(fileDescriptorProto, dependencies);
    }

    private static DescriptorProtos.FileDescriptorProto findServiceFileDescriptorProto(String packageName,
                                                                                       String serviceName,
                                                                                       Map<String, DescriptorProtos.FileDescriptorProto> fileDescriptorProtoMap) {
        for (DescriptorProtos.FileDescriptorProto proto : fileDescriptorProtoMap.values()) {
            if (proto.getPackage().equals(packageName)) {
                boolean exist = proto.getServiceList()
                    .stream()
                    .anyMatch(s -> serviceName.equals(s.getName()));
                if (exist) {
                    return proto;
                }
            }
        }
        throw new IllegalArgumentException("service not found");
    }

    private static Descriptors.FileDescriptor[] getDependencies(DescriptorProtos.FileDescriptorProto proto,
                                                                Map<String, DescriptorProtos.FileDescriptorProto> finalDescriptorProtoMap) {
        return proto.getDependencyList()
            .stream()
            .map(finalDescriptorProtoMap::get)
            .map(f -> toFileDescriptor(f, getDependencies(f, finalDescriptorProtoMap)))
            .toArray(Descriptors.FileDescriptor[]::new);
    }

    private static Descriptors.FileDescriptor toFileDescriptor(DescriptorProtos.FileDescriptorProto fileDescriptorProto,
                                                               Descriptors.FileDescriptor[] dependencies) {
        Descriptors.FileDescriptor res;
        try {
            res = Descriptors.FileDescriptor.buildFrom(fileDescriptorProto, dependencies);
        } catch (Descriptors.DescriptorValidationException ex) {
            throw new RuntimeException(ex);
        }
        return res;
    }

    private static DynamicMessage doInvoke(ManagedChannel channel,
                                           Descriptors.FileDescriptor fileDescriptor,
                                           Descriptors.MethodDescriptor originMethodDescriptor,
                                           Any message) throws Exception {

        // 重新生成 MethodDescriptor
        MethodDescriptor<DynamicMessage, DynamicMessage> methodDescriptor = generateMethodDescriptor(originMethodDescriptor);

        CallOptions callOptions = CallOptions.DEFAULT;

        TypeRegistry registry = TypeRegistry.newBuilder()
            .add(fileDescriptor.getMessageTypes())
            .build();

        // 将请求内容由 JSON 字符串转为相应的类型
        //JsonFormat.Parser parser = JsonFormat.parser().usingTypeRegistry(registry);
        DynamicMessage.Builder messageBuilder = DynamicMessage.newBuilder(originMethodDescriptor.getInputType());
        //parser.merge(requestContent, messageBuilder);
        //DynamicMessage requestMessage = messageBuilder.build();

        //ExtensionRegistry extensionRegistry = ExtensionRegistry.newInstance();
        //for (Descriptors.FieldDescriptor fieldDescriptor : originMethodDescriptor.getInputType().getFields()) {
        //    if (fieldDescriptor.isExtension()) {
        //        extensionRegistry.add(fieldDescriptor);
        //    }
        //}
        DynamicMessage requestMessage = methodDescriptor.parseRequest(new ByteArrayInputStream(message.getValue().toByteArray()));

        // 调用，调用方式可以通过 originMethodDescriptor.isClientStreaming() 和 originMethodDescriptor.isServerStreaming() 推断
        DynamicMessage response = ClientCalls.blockingUnaryCall(channel, methodDescriptor, callOptions, requestMessage);

        if (LOGGER.isDebugEnabled()) {
            JsonFormat.Printer printer = JsonFormat.printer().usingTypeRegistry(registry)
                .includingDefaultValueFields()
                .preservingProtoFieldNames()
                .omittingInsignificantWhitespace();
            String responseContent = printer.print(response);

            LOGGER.info("dapr grpc callback, invoke data: {}", printer.print(requestMessage));
            LOGGER.info("response type: " + response.getDescriptorForType().getFullName());
            LOGGER.info("response json: {}", responseContent);
        }

        return response;
    }

    private static MethodDescriptor<DynamicMessage, DynamicMessage> generateMethodDescriptor(Descriptors.MethodDescriptor originMethodDescriptor) {
        String fullMethodName = MethodDescriptor.generateFullMethodName(originMethodDescriptor.getService().getFullName(), originMethodDescriptor.getName());
        MethodDescriptor.Marshaller<DynamicMessage> inputTypeMarshaller = ProtoUtils.marshaller(DynamicMessage.newBuilder(originMethodDescriptor.getInputType())
            .buildPartial());
        MethodDescriptor.Marshaller<DynamicMessage> outputTypeMarshaller = ProtoUtils.marshaller(DynamicMessage.newBuilder(originMethodDescriptor.getOutputType())
            .buildPartial());
        return MethodDescriptor.<DynamicMessage, DynamicMessage>newBuilder()
            .setFullMethodName(fullMethodName)
            .setRequestMarshaller(inputTypeMarshaller)
            .setResponseMarshaller(outputTypeMarshaller)
            .setType(MethodDescriptor.MethodType.UNKNOWN)
            .build();
    }

    /**
     * Lists all topics subscribed by this app.
     * @param request
     * @param responseObserver
     */
    @Override
    public void listTopicSubscriptions(Empty request,
                                       StreamObserver<DaprAppCallbackProtos.ListTopicSubscriptionsResponse> responseObserver) {
        super.listTopicSubscriptions(request, responseObserver);
    }

    @Override
    public void onTopicEvent(DaprAppCallbackProtos.TopicEventRequest request,
                             StreamObserver<DaprAppCallbackProtos.TopicEventResponse> responseObserver) {
        super.onTopicEvent(request, responseObserver);
    }

    @Override
    public void listInputBindings(Empty request,
                                  StreamObserver<DaprAppCallbackProtos.ListInputBindingsResponse> responseObserver) {
        super.listInputBindings(request, responseObserver);
    }

    @Override
    public void onBindingEvent(DaprAppCallbackProtos.BindingEventRequest request,
                               StreamObserver<DaprAppCallbackProtos.BindingEventResponse> responseObserver) {
        super.onBindingEvent(request, responseObserver);
    }
}
