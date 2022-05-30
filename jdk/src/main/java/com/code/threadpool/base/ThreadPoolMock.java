package com.code.threadpool.base;

import java.io.IOException;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * ThreadPoolMock
 * desc：
 */
public class ThreadPoolMock {

    /**
     * 循环次数
     */
    private static final int COUNT = 100000;

    /**
     * SingleThreadExecutor
     */
    private static final ExecutorService singleThreadExecutor = Executors.newSingleThreadExecutor();

    /**
     * cachedThreadPool
     */
    private static final ExecutorService cachedThreadPool = Executors.newCachedThreadPool();

    /**
     * 执行
     *
     * @param num
     */
    public static void execute(int num) {

        ExecutorService executorService = num == 1 ? singleThreadExecutor : cachedThreadPool;

        // 计时开始
        long start = System.currentTimeMillis();
        AtomicInteger sum = new AtomicInteger();
        Random random = new Random(10);
        for (int i = 0; i < COUNT; i++) {
            int count = random.nextInt();
            if(i == 50) {
                System.out.println("中断线程了...");
                executorService.shutdownNow();
               // break;
            }
            System.out.println(i);
            executorService.execute(() -> {
                sum.addAndGet(count);
                try{
                    Thread.sleep(5000);
                }catch (Exception ignored){ }
            });
        }

        // 关闭线程池
        executorService.shutdownNow();
        // 打印结果和消耗时间
        System.out.println("result: " + sum + " cost_time:" + (System.currentTimeMillis() - start));

    }


    public static void main(String[] args) throws IOException {
        // 1. singleThreadExecutor
        // 2. cachedThreadPool
         execute(1);
        System.in.read();
    }
    /*
    1 调用shutdownnow()方法退出线程池时，线程池会向正在运行的任务发送Interrupt，任务中的阻塞操作会响应这个中断并抛出InterruptedException，
    但同时会清除线程的Interrupted 状态标识，导致后续流程感知不到线程的中断了。要想立即停止线程池中任务最好的方式就是直接向任务传递退出信号。
    2 Java用于中断线程而不是杀死线程，因此线程必须符合写这个模型的时候。因此，虽然（true）在你的程序中是不好的做法。相反，您需要查看您的线程是否已被中断，并自行关闭
    3 shutdownNow 是interrupt所有线程， 因此大部分线程将立刻被中断。之所以是大部分，而不是全部 ，是因为interrupt()方法能力有限。
如果线程中没有sleep 、wait、Condition、定时锁等应用, interrupt()方法是无法中断当前的线程的。所以，ShutdownNow()并不代表线程池就一定立即就能退出，它可能必须要等待所有正在执行的任务都执行完成了才能退出。
     */
}
