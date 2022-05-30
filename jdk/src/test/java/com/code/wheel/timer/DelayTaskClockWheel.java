package com.code.wheel.timer;

import com.code.wheel.factory.DelayTaskThreadFactory;
import io.netty.util.HashedWheelTimer;

import java.util.concurrent.TimeUnit;

/**
 * DelayTaskClockWheel
 *
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
