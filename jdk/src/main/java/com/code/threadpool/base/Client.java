package com.code.threadpool.base;

/**
 * Client
 *
 * @author <a href="mailto:libao.huang@yunhutech.com">shunhua</a>
 * @since 2020/12/05
 * <p>
 * desc：
 */
public class Client {

    public static final ThreadDemo thread = new ThreadDemo(Thread.currentThread().getName());

    public static void main(String[] args) {
        // 方法调用 - 方法级别调用
        thread.run();
        // 线程调用 - 线程级别调用
        thread.start();

       new Thread(() -> {
           //...
       }).start();
    }
}
