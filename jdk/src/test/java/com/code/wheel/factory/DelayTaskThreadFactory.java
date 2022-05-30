package com.code.wheel.factory;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * DelayTaskThreadFactory
 *
 * desc：
 */
public class DelayTaskThreadFactory implements ThreadFactory {
    private AtomicInteger count = new AtomicInteger(0);
    @Override
    public Thread newThread(Runnable r) {
        Thread t = new Thread(r);
        t.setName("DelayTaskTrigger-" + count.incrementAndGet());
        return t;
    }
}
