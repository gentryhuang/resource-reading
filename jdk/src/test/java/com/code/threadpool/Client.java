package com.code.threadpool;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * com.code.mixture.Client
 *
 * @author <a href="mailto:libao.huang@yunhutech.com">shunhua</a>
 * @since 2020/12/06
 * <p>
 * desc：
 */
public class Client {



    private static final int COUNT_BITS = Integer.SIZE - 3;

    private static final int CAPACITY   = (1 << COUNT_BITS) - 1;

    // runState is stored in the high-order bits
    private static final int RUNNING    = -1 << COUNT_BITS;
    private static final int SHUTDOWN   =  0 << COUNT_BITS;
    private static final int STOP       =  1 << COUNT_BITS;
    private static final int TIDYING    =  2 << COUNT_BITS;
    private static final int TERMINATED =  3 << COUNT_BITS;

    private static final AtomicInteger ctl = new AtomicInteger(ctlOf(RUNNING, 0));


    // Packing and unpacking ctl
    private static int runStateOf(int c)     { return c & ~CAPACITY; }
    private static int workerCountOf(int c)  { return c & CAPACITY; }

    private static int ctlOf(int rs, int wc) { return rs | wc; }

    private static boolean compareAndIncrementWorkerCount(int expect) {
        return ctl.compareAndSet(expect, expect + 1);
    }

    /**
     * Attempts to CAS-decrement the workerCount field of ctl.
     */
    private static boolean compareAndDecrementWorkerCount(int expect) {
        return ctl.compareAndSet(expect, expect - 1);
    }


    public static void main(String[] args) {

        System.out.println(COUNT_BITS + " : " + Integer.toBinaryString(COUNT_BITS));
        System.out.println(CAPACITY + " : " + Integer.toBinaryString(CAPACITY));
        System.out.println(RUNNING + " : " + Integer.toBinaryString(RUNNING));

        System.out.println(SHUTDOWN + " : " + Integer.toBinaryString(SHUTDOWN));
        System.out.println(STOP + " : " + Integer.toBinaryString(STOP));
        System.out.println(TIDYING + " : " + Integer.toBinaryString(TIDYING));
        System.out.println(TERMINATED + " : " + Integer.toBinaryString(TERMINATED));

        System.out.println(ctl + " : " + Integer.toBinaryString(ctl.get()));

        System.out.println("ctlOf()" + Integer.toBinaryString(ctlOf(RUNNING, 0)));


        System.out.println("workerCountOf " + workerCountOf(ctl.get()));


        System.out.println("ctl-before: "  + Integer.toBinaryString(ctl.get()));
        compareAndIncrementWorkerCount(ctl.get());
        System.out.println("ctl-after : " + Integer.toBinaryString(ctl.get()));
        System.out.println("workerCountOf " + workerCountOf(ctl.get()));
        compareAndDecrementWorkerCount(ctl.get());
        System.out.println("workerCountOf " + workerCountOf(ctl.get()));






    }


}
