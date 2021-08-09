package com.code.aqs;

/**
 * Test
 *
 * @author <a href="mailto:libao.huang@yunhutech.com">shunhua</a>
 * @since 2020/09/26
 * <p>
 * desc：
 */
public class Client {
    public static void main(String[] args) {
        AQS aqs = new AQS();
        AQS.Node node = new AQS.Node();
        aqs.enq(node);
//
//        Lock lock = new ReentrantLock();
//        Condition condition = lock.n ewCondition();
//
//        lock.lock();
//        try {
//            System.out.println("llalalal");
//            condition.wait();
//
//        } catch (Exception ex) {
//            System.out.println(ex);
//        } finally {
//            lock.unlock();
//        }
//
//        condition.signal();


    }





}
