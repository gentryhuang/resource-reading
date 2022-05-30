package com.code.concurrent;

/**
 * ThreadLocalPractice
 *
 * desc：
 */
public class ThreadLocalPractice {


    public static void main(String[] args) throws InterruptedException {

        // 1 创建 InheritableThreadLocal 对象
        ThreadLocal<String> threadLocal = new InheritableThreadLocal<>();

        // 2 创建的 ThreadLocalMap 挂载到当前 Thread 的 inheritableThreadLocals 属性
        threadLocal.set("小芒果!");

        // 3 创建并启动子线程
        Runnable runnable = () -> {
            System.out.println(Thread.currentThread().getName() + "-> " + threadLocal.get());
        };
        Thread thread = new Thread(runnable);
        thread.start();

        System.out.println(Thread.currentThread().getName() + "-> " + threadLocal.get());
        Thread.sleep(3000);
    }




}
