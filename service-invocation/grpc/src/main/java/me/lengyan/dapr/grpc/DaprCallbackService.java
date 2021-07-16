package me.lengyan.dapr.grpc;

import com.google.gson.Gson;
import com.google.protobuf.*;
import com.google.protobuf.util.JsonFormat;
import io.dapr.v1.AppCallbackGrpc;
import io.dapr.v1.CommonProtos;
import io.dapr.v1.DaprAppCallbackProtos;
import io.grpc.*;
import io.grpc.stub.StreamObserver;
import me.lengyan.dapr.core.Properties;
//import net.devh.boot.grpc.server.service.GrpcService;
import org.lognet.springboot.grpc.GRpcService;
import org.slf4j.Logger;

import static me.lengyan.dapr.grpc.utils.ReflectionUtils.doInvoke;
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

    @Override
    public void onInvoke(CommonProtos.InvokeRequest request,
                         StreamObserver<CommonProtos.InvokeResponse> responseObserver) {
        LOGGER.info("dapr grpc callback, invoke method: " + new Gson().toJson(request));

        // 这里最好是fullMethodName, 方便定位具体的Protocol和泛化调用, eg: a.b.c.UserService/getUserById
        String fullMethodName = request.getMethod().replaceAll("/", ".");

        try {
            final String fullServiceName = extraPrefix(fullMethodName);
            final String methodName = extraSuffix(fullMethodName);
            //final String packageName = extraPrefix(fullServiceName);
            //final String serviceName = extraSuffix(fullServiceName);

            Descriptors.ServiceDescriptor serviceDescriptor = ServiceManager.findServiceDescriptor(fullMethodName);
            if (serviceDescriptor == null) {
                LOGGER.error("service not found :{}", fullServiceName);
                throw new DaprAdaptorException("service not found :" + fullServiceName);
            }

            Descriptors.MethodDescriptor methodDescriptor = serviceDescriptor.findMethodByName(methodName);
            if (methodDescriptor == null) {
                LOGGER.error("service {} has not found method {}", fullServiceName, methodName);
                throw new DaprAdaptorException("can not found method " + methodName + "");
            }

            DynamicMessage resp = doInvoke(Properties.MAIN_CONTAINER_IP.get(), Properties.GRPC_SERVER_PORT.get(), methodDescriptor, request.getData());

            //if (LOGGER.isDebugEnabled()) {
            if (LOGGER.isInfoEnabled()) {
                TypeRegistry registry = TypeRegistry.newBuilder().add(serviceDescriptor.getFile().getMessageTypes()).build();
                JsonFormat.Printer printer = JsonFormat.printer().usingTypeRegistry(registry)
                    .includingDefaultValueFields()
                    .preservingProtoFieldNames()
                    .omittingInsignificantWhitespace();
                LOGGER.info("response for method {}:{}|{}",
                    fullMethodName, resp.getDescriptorForType().getFullName(), printer.print(resp));
            }

            // response to dapr
            CommonProtos.InvokeResponse.Builder daprRespBuilder = CommonProtos.InvokeResponse.newBuilder();
            daprRespBuilder.setData(Any.pack(resp));
            responseObserver.onNext(daprRespBuilder.build());
            responseObserver.onCompleted();

        } catch (Exception e) {
            LOGGER.error("dapr adaptor generic invoke failed, cause:" + e.getMessage(), e);
            Status status = Status.fromThrowable(e);
            if (status.getCode().equals(Status.Code.UNKNOWN)) {
                status = Status.INTERNAL;
            }
            responseObserver.onError(
                status.withDescription("dapr adaptor generic invoke failed, cause:" + e.getMessage()).asRuntimeException());
        }

    }

    private static String extraPrefix(String content) {
        return content.substring(0, content.lastIndexOf("."));
    }

    private static String extraSuffix(String content) {
        return content.substring(content.lastIndexOf(".") + 1);
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

    /**
     * Subscribes events from Pubsub
     * @param request
     * @param responseObserver
     */
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
