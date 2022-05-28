package com.code.wheel;

import io.netty.util.HashedWheelTimer;
import io.netty.util.Timeout;
import io.netty.util.Timer;
import io.netty.util.TimerTask;

import java.util.concurrent.TimeUnit;

/**
 * DelayTaskTest
 *
 * @author <a href="mailto:libao.huang@yunhutech.com">shunhua</a>
 * @since 2021/07/23
 * <p>
 * desc：
 */
public class HashedWheelTimerClient {
    public static void main(String[] args) {

        // 1 创建一个 Timer，内部参数全部使用默认值
        Timer timer = new HashedWheelTimer();

        // 2 编写 TimeTask 任务
        TimerTask timerTask = new TimerTask() {
            @Override
            public void run(Timeout timeout) throws Exception {
                System.out.println(Thread.currentThread().getName() + "is working !");
            }
        };

        // 3 提交任务
        // 创建 HashedWheelTimeout 对象，并将该对象放入任务队列中，等待被加入到 Hash 轮中
        Timeout timeout = timer.newTimeout(timerTask, 1, TimeUnit.SECONDS);

    }
}
