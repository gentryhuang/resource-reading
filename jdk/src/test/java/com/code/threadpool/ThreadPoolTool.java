package com.code.threadpool;

import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * ThreadPoolTool
 *
 * @author <a href="mailto:libao.huang@yunhutech.com">shunhua</a>
 * @since 2022/06/12
 * <p>
 * desc：
 */
public class ThreadPoolTool {

    public static void main(String[] args) {



        ThreadPoolExecutor executorService = (ThreadPoolExecutor) Executors.newCachedThreadPool();

        for (int i = 0; i < 10; i++) {
            executorService.execute(() -> {
                System.out.println(Thread.currentThread().getName() + " is running !");
            });
        }

        System.out.println("等待任务都执行完毕，并观察线程池的状态！");

        try {
            Thread.sleep(70000);
        } catch (Exception exception) {
            exception.printStackTrace();
        }

        System.out.println("线程池是否关闭：" + executorService.isTerminated());
        for (int i = 0; i < 3; i++) {
            System.out.println("线程池中存活的线程数 " + executorService.getPoolSize());

            try {
                Thread.sleep(20000);
            } catch (Exception exception) {
                exception.printStackTrace();
            }
        }
        System.out.println("线程池中任务数量 " + executorService.getTaskCount());
    }
}
