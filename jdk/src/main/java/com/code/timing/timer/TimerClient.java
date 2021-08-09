package com.code.timing.timer;

import java.util.Timer;
import java.util.TimerTask;

/**
 * TimerClient
 *
 * @author <a href="mailto:libao.huang@yunhutech.com">shunhua</a>
 * @since 2021/07/24
 * <p>
 * desc：
 */
public class TimerClient {

    public static void main(String[] args) {
        timer1();
    }

    public static void timer1() {
        Timer timer = new Timer();

        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                System.out.println("timer1 task1 is running !");
                int a = 1 / 0;
            }
        }, 2000);


        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                System.out.println("timer1 task2 is running !");
            }
        }, 3000);

    }




}
