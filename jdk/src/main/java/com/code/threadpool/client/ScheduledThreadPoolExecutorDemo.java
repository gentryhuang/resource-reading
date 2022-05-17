package com.code.threadpool.client;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * ScheduledThreadPoolExecutorDemo
 * desc：
 */
public class ScheduledThreadPoolExecutorDemo {

    public static void main(String[] args) throws IOException {
        ScheduledThreadPoolExecutor scheduledThreadPoolExecutor = new ScheduledThreadPoolExecutor(0);
        // 设置 线程池关闭后（shutdown)继续执行存在的周期性任务
        // scheduledThreadPoolExecutor.setContinueExistingPeriodicTasksAfterShutdownPolicy(true);
        // scheduledThreadPoolExecutor.setExecuteExistingDelayedTasksAfterShutdownPolicy(true);

        // 线程池关闭后（shutdown)是否继续执行存在的周期性任务（包括 fixedRate/fixedDelay）
        System.out.println("existingPeriodicTasksAfterShutdownPolicy: " + scheduledThreadPoolExecutor.getContinueExistingPeriodicTasksAfterShutdownPolicy());
        // 线程关闭后（shutdown）是否继续执行存在的延迟任务
        System.out.println("existingDelayedTasksAfterShutdownPolicy: " + scheduledThreadPoolExecutor.getExecuteExistingDelayedTasksAfterShutdownPolicy());


        // 1 固定频率 - 每 2s 执行一次
        ScheduledFuture<?> scheduledFuture = scheduledThreadPoolExecutor.scheduleAtFixedRate(() -> {
            System.out.println(Thread.currentThread().getName() + " "+LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd hh:mm:ss")) + " 固定频率执行....");
        }, 1, 4, TimeUnit.SECONDS);


        // 2 固定延迟时间 - 每 3s 执行一次
        ScheduledFuture<?> scheduledFuture1 = scheduledThreadPoolExecutor.scheduleWithFixedDelay(() -> {
            System.out.println(Thread.currentThread().getName() + " "+LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd hh:mm:ss")) + " 固定延迟时间执行....");
        }, 1, 2, TimeUnit.SECONDS);


        // 3 延迟调度 - 执行无返回值任务
        ScheduledFuture<?> scheduledFuture2 = scheduledThreadPoolExecutor.schedule(() -> {
            System.out.println(Thread.currentThread().getName() + " "+"schedule...runnable...");
        }, 1, TimeUnit.SECONDS);


        // 4 延迟调度 - 执行有返回值任务
        ScheduledFuture<Object> scheduledFuture3 = scheduledThreadPoolExecutor.schedule(() -> {
            System.out.println(Thread.currentThread().getName() + " "+"schedule...callable...");
            return "future";
        }, 1, TimeUnit.SECONDS);
        try {
            Object o = scheduledFuture3.get();
            System.out.println(o);
        } catch (Exception ex) {
            ex.printStackTrace();
        }

        // 5 关闭线程池
        scheduledThreadPoolExecutor.shutdown();

        try {
            System.out.println("10s 后开始停止线程池，届时所有还未执行的任务会被终止！");
            Thread.sleep(10000);
        } catch (Exception ex) {
            ex.printStackTrace();
        }

        // 6 停止线程池
        List<Runnable> runnables = scheduledThreadPoolExecutor.shutdownNow();
        System.out.println("还未执行的任务数：" + runnables.size());
    }
}
