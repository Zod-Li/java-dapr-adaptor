package me.lengyan.dapr.adaptor.grpc;

import io.dapr.client.DaprClient;
import io.dapr.client.DaprClientBuilder;
import io.dapr.client.domain.HttpExtension;
import io.grpc.ClientCall;
import io.grpc.ForwardingClientCall;
import io.grpc.Metadata;
import reactor.core.publisher.Mono;

import java.lang.reflect.ParameterizedType;
import java.util.HashMap;
import java.util.Map;

/**
 * Dapr proxy
 * 装饰器?
 *
 * @param <ReqT>
 * @param <RespT>
 */
public class DaprClientCall<ReqT, RespT> extends ForwardingClientCall<ReqT, RespT> {

    private DaprClient daprClient;

    private ClientCall<ReqT, RespT> delegate;
    private Listener<RespT> delegateListener;

    private Map<String, String> metadata = new HashMap<>();

    public DaprClientCall(ClientCall<ReqT, RespT> delegate) {
        this.delegate = delegate;
        daprClient = new DaprClientBuilder().build();
    }

    //private class ForwardingMessageListener extends ClientCall.Listener<RespT> {
    //    @Override
    //    public void onMessage(RespT message) {
    //        // 将dapr调用成功返回的response转发给被代理的stub
    //        delegateListener.onMessage(message);
    //    }
    //}

    @Override
    public void sendMessage(ReqT message) {
        // TODO 先写死, 后面实现一个转发白名单, 支持局部使用
        if ("".equals("proto.CiJobService/getByJobId") && "".equals("getDeployToken")) {
            // 转发message到dapr control-plane

            Mono<RespT> resp = daprClient.invokeMethod("e-ci-manager", "getByJobId", message, HttpExtension.NONE, metadata, getRespClazz());
            // TODO 错误处理, 如何给errorMessage


            // 触发listener的onMessage, 转给delegate
            Listener<RespT> listener = getDelegateListener();
            listener.onMessage(resp.block());

            // 当前信道不需要继续通信
            //super.sendMessage(message);
        } else {
            super.sendMessage(message);
        }
    }

    @Override
    public void start(Listener<RespT> responseListener, Metadata headers) {
        this.delegateListener = responseListener;

        // metadata需不需要设置
        headers.keys().forEach(key -> {
        });

        //new ClientCall.Listener<RespT>(){
            // message由dapr负责通信, 故当前
        //};
        //daprClient.waitForSidecar(3000);

        super.start(responseListener, headers);
    }

    private Listener<RespT> getDelegateListener() {
        if (this.delegateListener == null) {
            throw new IllegalArgumentException("delegate responseListener not found");
        }
        return this.delegateListener;
    }

    @Override
    protected ClientCall<ReqT, RespT> delegate() {
        return delegate;
    }

    @SuppressWarnings("unchecked")
    private Class<RespT> getRespClazz() {
        ParameterizedType pt = (ParameterizedType) this.getClass().getGenericSuperclass();
        return (Class<RespT>) pt.getActualTypeArguments()[1];
    }
}
