package com.code.aqs;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Test
 *
 * desc：
 */
public class Client {
    public static void main(String[] args) {
        AQS aqs = new AQS();
        AQS.Node node = new AQS.Node();
        aqs.enq(node);

        Lock lock = new ReentrantLock();
        Condition condition = lock.newCondition();

        lock.lock();
        try {
            System.out.println("llalalal");
            condition.wait();

        } catch (Exception ex) {
            System.out.println(ex);
        } finally {
            lock.unlock();
        }

        condition.signal();


    }





}
