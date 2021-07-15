package me.lengyan.dapr.grpc.utils;

import com.google.protobuf.*;
import io.grpc.CallOptions;
import io.grpc.ManagedChannel;
import io.grpc.MethodDescriptor;
import io.grpc.protobuf.ProtoUtils;
import io.grpc.stub.ClientCalls;
import org.slf4j.Logger;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static org.slf4j.LoggerFactory.getLogger;

public class ReflectionUtils {

    private static final Logger LOGGER = getLogger(ReflectionUtils.class);

    public static Descriptors.FileDescriptor getFileDescriptor(List<ByteString> fileDescriptorProtoList,
                                                               String packageName,
                                                               String serviceName) throws Exception {

        Map<String, DescriptorProtos.FileDescriptorProto> fileDescriptorProtoMap =
            fileDescriptorProtoList.stream()
                .map(bs -> {
                    try {
                        return DescriptorProtos.FileDescriptorProto.parseFrom(bs);
                    } catch (InvalidProtocolBufferException e) {
                        LOGGER.error(e.getMessage(), e);
                    }
                    return null;
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(DescriptorProtos.FileDescriptorProto::getName, f -> f));

        if (fileDescriptorProtoMap.isEmpty()) {
            LOGGER.error("service not found");
            throw new IllegalArgumentException("service not found");
        }

        // 查找服务对应的Proto描述
        DescriptorProtos.FileDescriptorProto fileDescriptorProto = findServiceFileDescriptorProto(packageName, serviceName, fileDescriptorProtoMap);
        // 获取Proto依赖
        Descriptors.FileDescriptor[] dependencies = getDependencies(fileDescriptorProto, fileDescriptorProtoMap);
        return Descriptors.FileDescriptor.buildFrom(fileDescriptorProto, dependencies);
    }

    public static DynamicMessage doInvoke(ManagedChannel channel,
                                          Descriptors.FileDescriptor fileDescriptor,
                                          Descriptors.MethodDescriptor originMethodDescriptor,
                                          Any message) throws Exception {

        MethodDescriptor<DynamicMessage, DynamicMessage> methodDescriptor = generateMethodDescriptor(originMethodDescriptor);
        //TypeRegistry registry = TypeRegistry.newBuilder()
        //    .add(fileDescriptor.getMessageTypes())
        //    .build();
        //JsonFormat.Parser parser = JsonFormat.parser().usingTypeRegistry(registry);
        //DynamicMessage.Builder messageBuilder = DynamicMessage.newBuilder(originMethodDescriptor.getInputType());
        //parser.merge(requestContent, messageBuilder);
        //DynamicMessage requestMessage = messageBuilder.build();

        DynamicMessage requestMessage = methodDescriptor.parseRequest(new ByteArrayInputStream(message.getValue().toByteArray()));

        // 调用，调用方式可以通过 originMethodDescriptor.isClientStreaming() 和 originMethodDescriptor.isServerStreaming() 推断
        return ClientCalls.blockingUnaryCall(channel, methodDescriptor, CallOptions.DEFAULT, requestMessage);
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
}
