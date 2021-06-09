package me.lengyan.dapr.grpc;

public class DaprAdaptorException extends RuntimeException{

    public DaprAdaptorException(String message) {
        super(message);
    }

    public DaprAdaptorException(String message, Throwable cause) {
        super(message, cause);
    }

}
