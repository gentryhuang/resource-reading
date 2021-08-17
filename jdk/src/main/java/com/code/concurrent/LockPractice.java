package com.code.concurrent;

import java.util.concurrent.locks.ReentrantLock;

/**
 * LockPratice
 *
 * @author <a href="mailto:libao.huang@yunhutech.com">shunhua</a>
 * @since 2021/08/10
 * <p>
 * desc：
 */
public class LockPractice {

    public static void main(String[] args) {
        ReentrantLock reentrantLock = new ReentrantLock();

        reentrantLock.lock();
        System.out.println("lock ...");
        reentrantLock.unlock();

    }

}
