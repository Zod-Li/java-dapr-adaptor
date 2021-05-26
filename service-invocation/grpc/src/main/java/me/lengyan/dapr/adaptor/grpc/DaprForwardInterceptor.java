package me.lengyan.dapr.adaptor.grpc;

import io.grpc.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DaprForwardInterceptor implements ClientInterceptor {

    private static final Logger LOGGER = LoggerFactory.getLogger(DaprForwardInterceptor.class);

    // TODO
    //  route mapping
    //  enabled dapr adaptor service name white list

    // 通过SPI加载的构造器
    public DaprForwardInterceptor() {
    }

    /**
     * MethodDescriptor{fullMethodName=proto.CiJobService/getByJobId, type=UNARY, idempotent=false, safe=false,
     * sampledToLocalTracing=true, requestMarshaller=io.grpc.protobuf.lite.ProtoLiteUtils$MessageMarshaller@7c8b9583,
     * responseMarshaller=io.grpc.protobuf.lite.ProtoLiteUtils$MessageMarshaller@5f0db122,
     * schemaDescriptor=proto.ci.CiJobServiceGrpc$CiJobServiceMethodDescriptorSupplier@7b2bc162}
     * CallOptions{deadline=8.421187241s from now, authority=null, callCredentials=null, executor=class
     * io.grpc.stub.ClientCalls$ThreadlessExecutor, compressorName=null, customOptions=[], waitForReady=false,
     * maxInboundMessageSize=null, maxOutboundMessageSize=null, streamTracerFactories=[]} Endpoint{host='e-ci-manager',
     * port=15751}
     *
     * @param methodDescriptor
     * @param callOptions
     * @param channel
     * @param <ReqT>
     * @param <RespT>
     * @return
     */
    @Override
    public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(MethodDescriptor<ReqT, RespT> methodDescriptor,
                                                               CallOptions callOptions, Channel channel) {

        // TODO protocol服务提供者注册解析, 扫描io.grpc.BindableService

        // TODO k8s service dns, 解析app-id

        // k8s-svc & dapr-app-id, etcd-service-name

        // proto service name
        String serviceId = methodDescriptor.getServiceName();
        // proto service method
        String method = methodDescriptor.getFullMethodName();

        LOGGER.info("GRPC client call - serviceName : {}, method: {}", serviceId, method);

        //if (channel instanceof net.coding.common.rpc.impl.ChannelHandler) {
        // 平台部实现, 包访问级别的class
        //ChannelHandler

        //}

        ClientCall<ReqT, RespT> clientCall = channel.newCall(methodDescriptor, callOptions);
        return new DaprClientCall<>(clientCall);
        //return new ForwardingClientCall.SimpleForwardingClientCal l(cc) {
        //    @Override
        //    public void start(Listener responseListener, Metadata headers) {
        //        super.start(responseListener, headers);
        //    }
        //};
    }

    private String findChannelTarget(Channel channel) {
        // TODO 存在两种情况
        //  1. 基于服务注册发现机制获取的ip地址(LB后) , PS 如果拿到的只是一个IP那就一点办法都没有了  只能从注册时的ServiceName着手, 对于CODING来说可以在规范上统一避免这种方式,  但是不利于通用化
        //  2. 基于K8S服务发现的SVC  (可以作为app-id的同名映射)

        // 利用启动阶段扫描所有GRPC Service Provider提供的protos, 注册到ZK? 然后转发时候利用这个映射

        return "";
    }
}