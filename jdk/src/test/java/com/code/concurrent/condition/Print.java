package com.code.concurrent.condition;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Print
 *
 * desc：
 */
public class Print {

    ReentrantLock reentrantLock = new ReentrantLock();
    Condition condition = reentrantLock.newCondition();

    public void print() throws InterruptedException {
        System.out.println(Thread.currentThread().getName() + " 准备执行任务...");

        reentrantLock.lock();
        System.out.println("isHeldExclusively: " + reentrantLock.isHeldByCurrentThread());
        System.out.println("state: " + reentrantLock.getHoldCount());
        System.out.println("sync_queue: " + reentrantLock.getQueueLength());
        System.out.println("condition_queue " + reentrantLock.getWaitQueueLength(condition));
        try {

            // int a = 1 / 0;
            condition.await();
            System.out.println("action ...");

        } catch (Exception ex) {
            // ...
        } finally {
             reentrantLock.unlock();
            //  System.out.println("unlock");
        }
    }



}
