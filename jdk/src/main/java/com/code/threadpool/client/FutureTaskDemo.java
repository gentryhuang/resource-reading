package com.code.threadpool.client;

import javax.sound.midi.Soundbank;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.concurrent.*;

/**
 * FutureTaskDemo
 *
 * <p>
 * desc：
 */
public class FutureTaskDemo {

    public static void testComp() throws ExecutionException, InterruptedException {
        FutureTask<String> futureTask = new FutureTask<>(() -> {
            System.out.println("hello future-task");
        }, null);

        // 是否开始执行
        System.out.println("isDone() - " + futureTask.isDone());

        // 是否被取消
        System.out.println("isCanceled() - " + futureTask.isCancelled());

        // 执行任务
        futureTask.run();

        // 取消任务
        futureTask.cancel(true);

        // 是否开始执行
        System.out.println("isDone() - " + futureTask.isDone());

        // 是否被取消
        System.out.println("isCanceled() - " + futureTask.isCancelled());

        // 获取结果
        System.out.println(futureTask.get());

    }

    public static void testException() throws ExecutionException, InterruptedException {
        FutureTask<String> futureTask = new FutureTask<>(() -> {
            System.out.println("hello future-task");
        }, "hello future-task!");


        // 是否开始执行
        System.out.println("isDone() - " + futureTask.isDone());

        futureTask.cancel(false);

        // 是否开始执行
        System.out.println("isDone() - " + futureTask.isDone());

        futureTask.run();

        // 是否开始执行
        System.out.println("isDone() - " + futureTask.isDone());

        // 是否被取消
        System.out.println("isCanceled() - " + futureTask.isCancelled());

        // 获取结果
        System.out.println(futureTask.get());

    }

    public static void futureTaskTest() {
        // 1 创建固定大小线程池
        ExecutorService threadPool = Executors.newFixedThreadPool(8);
        Random random = new Random(10000);

        // 2 定义异步计算任务集合
        HashSet<Future<String>> futureList = new HashSet<>();
        // List<Future<String>> futureList = new ArrayList<>();
        for (int i = 1; i <= 100; i++) {
            // submit 方法提交的任务会被 FutureTask 包装，返回的也是这个 FutureTask 对象
            int finalI = i;
            Future<String> future = threadPool.submit(() -> {
                        try {
                            Thread.sleep(random.nextInt(10000));
                        } catch (InterruptedException interruptedException) {
                            Thread.currentThread().interrupt();
                        }
                        return Thread.currentThread().getName() + " is run future-task..." + finalI;
                    }
            );
            futureList.add(future);
        }

        // 3 任务提交完成后，等待处理完成并打印
        futureList.forEach(future -> {
            try {
                // 任务取消，不获取结果{@link get()}，否则会抛出异常
                if (future.isCancelled()) {
                    return;
                }

                // 任务已经在执行了，则阻塞等待最终的结果
                if (future.isDone()) {
                    System.out.println(future.get(3, TimeUnit.SECONDS));

                } else {
                    // 执行到这里，说明异步计算任务还是 NEW 状态，阻塞等待被调度、执行
                    System.out.println(future.get());
                }

            } catch (InterruptedException | ExecutionException | TimeoutException e) {
                e.printStackTrace();
            }
        });

    }

    private static void testCase(int no) throws ExecutionException, InterruptedException {
        switch (no) {
            case 1:
                testComp();
                break;
            case 2:
                testException();
                break;
            case 3:
                futureTaskTest();
                break;
            default:
                break;
        }
    }


    public static void main(String[] args) throws ExecutionException, InterruptedException {
        testCase(3);
    }
}
