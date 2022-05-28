package com.code.concurrent.tool;

import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.*;

/**
 * CyclicBarrierDemo
 *
 * @author <a href="mailto:libao.huang@yunhutech.com">shunhua</a>
 * @since 2020/10/11
 * <p>
 * desc：
 */
@Slf4j
public class CyclicBarrierDemo {

    /**
     * 固定线程数线程池
     */
    public static ExecutorService service = Executors.newFixedThreadPool(8);

    public static void main(String[] args) {

        // 要等待 4 个同学到齐，到齐后统一，因此这里初始化一个带有 Runnable 参数的 CyclicBarrier
        CyclicBarrier cyclicBarrier = new CyclicBarrier(4, () -> {

            try{
                log.info("4人已到齐，请系好安全带，现在出发赶往目的地 !");
                Thread.sleep(15000);
            }catch (Exception e){

            }
        });

        // 8个人，需要 2 辆车。这里会循环使用 CyclicBarrier
        for (int i = 0; i < 8; i++) {

            service.submit(() -> {
                try {
                    // 赶往拼车地点
                    Thread.sleep((long) (Math.random() * 10000));

                    log.info("到达指定拼车地点 !");
                    cyclicBarrier.await(5, TimeUnit.SECONDS); // todo 唤醒线程抢锁问题

                    log.info("出发了 !");
                } catch (InterruptedException | BrokenBarrierException | TimeoutException exception) {
                    exception.printStackTrace();
                }
            });
        }
    }
}
