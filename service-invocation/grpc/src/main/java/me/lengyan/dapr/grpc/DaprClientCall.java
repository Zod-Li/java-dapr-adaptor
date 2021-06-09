package me.lengyan.dapr.grpc;

import com.google.gson.Gson;
import com.google.protobuf.Message;
import com.google.protobuf.util.JsonFormat;
import io.dapr.client.DaprClient;
import io.dapr.client.DaprClientBuilder;
import io.dapr.client.domain.HttpExtension;
import io.dapr.utils.TypeRef;
import io.grpc.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;

/**
 * @author lengyan.lby
 */
public class DaprClientCall<ReqT, RespT> extends ForwardingClientCall<ReqT, RespT> {

    private static final Logger LOGGER = LoggerFactory.getLogger(DaprClientCall.class);

    private final ClientCall<ReqT, RespT> delegate;
    private final MethodDescriptor<ReqT, RespT> methodDescriptor;

    // dest dapr app & method
    private final String appId;
    private final String methodName;

    private Listener<RespT> delegateListener;
    private Map<String, String> headers = new HashMap<>();

    public DaprClientCall(String appId, MethodDescriptor<ReqT, RespT> methodDescriptor, ClientCall<ReqT, RespT> delegate) {
        this.appId = appId;
        this.methodDescriptor = methodDescriptor;
        this.delegate = delegate;
        this.methodName = methodDescriptor.getFullMethodName();
    }

    @Override
    public void sendMessage(ReqT message) {
        //  目前只能实现unaryCall和异步, streaming方式的调用还不支持
        // TODO traceId关联dapr?
        // TODO 通信协议采用CloudEvent?
        try (DaprClient daprClient = new DaprClientBuilder().build()) {

            JsonFormat.Printer printer = null;
            if (LOGGER.isDebugEnabled()) {
                printer = JsonFormat.printer().includingDefaultValueFields()
                    .preservingProtoFieldNames()
                    .omittingInsignificantWhitespace();
                LOGGER.debug("request {} with data: {}, metadata: {}",
                    methodName, printer.print((Message)message), new Gson().toJson(headers));
            }

            // TODO 默认使用bytes传输, 考虑bundle化Serializer以支持json/CloudEvent等
            byte[] resp = daprClient.invokeMethod(appId, methodName,
                ((Message)message).toByteString().toByteArray(), HttpExtension.NONE, headers, TypeRef.BYTE_ARRAY).block();

            if (resp != null) {
                // 默认情况下(不改变序列化实现)使用json
                RespT respT = methodDescriptor.getResponseMarshaller().parse(new ByteArrayInputStream(resp));

                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("get response: {}", printer.print((Message)respT));
                }

                getDelegateListener().onMessage(respT);
                getDelegateListener().onClose(Status.OK, null);
            } else {
                // can not reach
                // TODO 返回值为Void类型时, resp是否为null或0字节?
                getDelegateListener().onMessage(null);
                getDelegateListener().onClose(Status.DATA_LOSS, null);
            }
        } catch (Exception e) {
            LOGGER.error("something went wrong", e);
            // TODO 代理层出现问题时考虑是否支持降级
            getDelegateListener().onClose(Status.INTERNAL, null);
        }
    }

    @Override
    public void start(Listener<RespT> responseListener, Metadata metadata) {
        this.delegateListener = responseListener;
        metadata.keys().forEach(k -> {
            Object value = k.endsWith("-bin") ? metadata.get(Metadata.Key.of(k, Metadata.BINARY_BYTE_MARSHALLER))
                : metadata.get(Metadata.Key.of(k, Metadata.ASCII_STRING_MARSHALLER));
            if (value != null) {
                headers.put(k, value.toString());
            }
        });
        super.start(responseListener, metadata);
    }

    private Listener<RespT> getDelegateListener() {
        if (this.delegateListener == null) {
            throw new IllegalArgumentException("responseListener not found");
        }
        return this.delegateListener;
    }

    @Override
    protected ClientCall<ReqT, RespT> delegate() {
        return delegate;
    }

    private Type getRespType() {
        ParameterizedType pt = (ParameterizedType) this.getClass().getGenericSuperclass();
        return pt.getActualTypeArguments()[1];
    }
}
