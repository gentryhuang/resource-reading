package com.code.concurrent;

import java.util.concurrent.locks.AbstractQueuedSynchronizer;

/**
 * Mutex
 *
 * @author <a href="mailto:libao.huang@yunhutech.com">shunhua</a>
 * @since 2021/08/14
 * <p>
 * desc：
 */

/**
 * 借助 AQS 实现锁功能，不支持重入
 */
public class Mutex {

    // 1 定义静态内部类 Sync 继承 AQS
    private static class Sync extends AbstractQueuedSynchronizer {

        // 2 实现 tryAcquire-tryRelease
        @Override
        protected boolean tryAcquire(int arg) {
            return compareAndSetState(0, 1);
        }
        @Override
        protected boolean tryRelease(int arg) {
            setState(0);
            return true;
        }

    }

    // 3 将 AQS 实现组合 Mutex 中
    private Sync sync = new Sync();
    public void lock() {
        sync.acquire(1);
    }
    public void unlock() {
        sync.release(1);
    }
}
