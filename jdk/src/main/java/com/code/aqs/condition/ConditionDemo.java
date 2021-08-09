package com.code.aqs.condition;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;


/**
 * ConditionDemo
 *
 * @author <a href="mailto:libao.huang@yunhutech.com">shunhua</a>
 * @since 2020/12/26
 * <p>
 * desc：
 */
public class ConditionDemo {

    static ExecutorService executorService = Executors.newFixedThreadPool(5);

    public static void main(String[] args) throws InterruptedException, IOException {

        Print print = new Print();

        Runnable runnable = () -> {
            try {
                print.print();
            } catch (Exception exception) {
                exception.printStackTrace();
            }
        };

        for (int i = 0; i < 20; i++) {
            executorService.execute(runnable);
        }

        System.in.read();

    }


    ReentrantLock lock = new ReentrantLock();
    Condition condition = lock.newCondition();

    public void await() throws InterruptedException {
        lock.lock();
        try {
            // business
            condition.await();
        } finally {
            lock.unlock();
        }
    }

    public void signal() {
        lock.lock();
        try {
            // business
            condition.signal();
        } finally {
            lock.unlock();
        }
    }


}
