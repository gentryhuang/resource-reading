package com.code.wheel.task;

import io.netty.util.Timeout;
import io.netty.util.TimerTask;

/**
 * DelayTask
 *
 * @author <a href="mailto:libao.huang@yunhutech.com">shunhua</a>
 * @since 2021/07/23
 * <p>
 * desc：
 */
public class DelayTask implements TimerTask {

    @Override
    public void run(Timeout timeout) throws Exception {
        System.out.println("timeout:" + timeout.toString());
    }
}
