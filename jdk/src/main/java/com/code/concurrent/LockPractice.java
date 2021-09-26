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


        new Thread(()->{
            reentrantLock.lock();
            System.out.println(Thread.currentThread().getName());
            reentrantLock.unlock();
        }).start();

        new Thread(()->{
            reentrantLock.lock();
            System.out.println(Thread.currentThread().getName());
        }).start();

        new Thread(()->{
            reentrantLock.lock();
            System.out.println(Thread.currentThread().getName());
        }).start();

        reentrantLock.lock();
        System.out.println("lock ...");
        reentrantLock.unlock();

    }


}
