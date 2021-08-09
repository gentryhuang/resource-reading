package com.code.timing.hashedWheelTimer.timer;

import com.code.timing.hashedWheelTimer.factory.DelayTaskThreadFactory;
import io.netty.util.HashedWheelTimer;

import java.util.concurrent.TimeUnit;

/**
 * DelayTaskClockWheel
 *
 * @author <a href="mailto:libao.huang@yunhutech.com">shunhua</a>
 * @since 2021/07/23
 * <p>
 * desc：
 */
public class DelayTaskClockWheel {
    private final static HashedWheelTimer wheelTimer = new HashedWheelTimer(
            new DelayTaskThreadFactory(),
            1000,
            TimeUnit.MILLISECONDS, 64
    );

    public static HashedWheelTimer getWheelTimer() {
        return wheelTimer;
    }
}
