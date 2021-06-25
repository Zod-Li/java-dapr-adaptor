package me.lengyan.dapr.core;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The default thread factory
 * @author lengyan.lby
 */
public class DefaultThreadFactory implements ThreadFactory {

    private static final AtomicInteger POLL_NUMBER = new AtomicInteger(1);
    private final ThreadGroup group;
    private final AtomicInteger threadNumber = new AtomicInteger(1);
    private final String namePrefix;

    public DefaultThreadFactory(String groupName) {
        this(groupName, "pool");
    }

    public DefaultThreadFactory(String groupName, String theadNamePrefix) {
        group = new ThreadGroup(groupName);
        namePrefix = theadNamePrefix + "-" + POLL_NUMBER.getAndIncrement() + "-thread-";
    }

    @Override
    public Thread newThread(Runnable r) {
        Thread t = new Thread(group, r,
                namePrefix + threadNumber.getAndIncrement(),
                0);
        if (t.isDaemon()) {
            t.setDaemon(false);
        }
        if (t.getPriority() != Thread.NORM_PRIORITY) {
            t.setPriority(Thread.NORM_PRIORITY);
        }
        t.start();
        return t;
    }

}
