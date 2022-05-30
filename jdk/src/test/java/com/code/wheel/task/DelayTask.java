package com.code.wheel.task;

import io.netty.util.Timeout;
import io.netty.util.TimerTask;

/**
 * DelayTask
 *
 * desc：
 */
public class DelayTask implements TimerTask {

    @Override
    public void run(Timeout timeout) throws Exception {
        System.out.println("timeout:" + timeout.toString());
    }
}
