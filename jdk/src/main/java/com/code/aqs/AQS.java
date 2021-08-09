package com.code.aqs;

import lombok.Data;
import sun.misc.Unsafe;

import java.io.Serializable;
import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Client
 *
 * @author <a href="mailto:libao.huang@yunhutech.com">shunhua</a>
 * @since 2020/09/27
 * <p>
 * desc：
 */
@Data
public class AQS implements Serializable {


    @Data
    static final class Node {
        private static final long serialVersionUID = -7369699168982654308L;
        /**
         * 前置节点
         */
        private Node pre;
        /**
         * 后置节点
         */
        private Node next;
        /**
         * waitStatus
         */
        private int waitStatus;
    }

    /**
     * head
     */
    private Node head;
    /***
     * tail
     */
    private Node tail;




    /**
     * Unsafe
     */
    private static Unsafe unsafe = null;
    /**
     * CAS 操作 AQS的tail属性
     */
    private static long nextOffset;
    /**
     * Node 的 waiterStatus
     */
    private static  long waitStatusOffset;

    static {

        try {
            Field field = Unsafe.class.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            unsafe = (Unsafe) field.get(null);

            // 指定对AQS的tail属性进行CAS操作
            nextOffset = unsafe.objectFieldOffset(AQS.class.getDeclaredField("tail"));

            waitStatusOffset = unsafe.objectFieldOffset
                    (AQS.Node.class.getDeclaredField("waitStatus"));

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public final boolean compareAndSetNext(Node expect, Node update) {
        return unsafe.compareAndSwapObject(this, nextOffset, expect, update);
    }

    public final boolean compareAndSetWaitStatus(Node node, int expect,int update){
        return unsafe.compareAndSwapInt(node, waitStatusOffset,
                expect, update);
    }


    public void enq(Node node) {

        tail = head = new Node();
        Node t = tail;


        System.out.println("head->hashcode:" + System.identityHashCode(head));
        System.out.println("tail->hashcode:" + System.identityHashCode(tail));



        System.out.println("*****************");

        /** 等价 tail=null;tail=node*/
        //compareAndSetNext(t, node);
        tail = node;

        System.out.println("t->hashcode:" + System.identityHashCode(t));
        System.out.println("head->hashcode:" + System.identityHashCode(head));

        System.out.println("tail->hashcode:" + System.identityHashCode(tail));
        System.out.println("node->hashcode:" + System.identityHashCode(node));


        /**
         * 原子Integer
         * CAS操作包含三个操作数：
         * 内存位置、预期原值及新值。执行CAS操作的时候，将内存位置的值与预期原值比较，如果相匹配，那么处理器会自动将该位置值更新为新值，否则，处理器不做任何操作。
         */
        AtomicInteger atomicInteger = new AtomicInteger();

        boolean integerFlag = atomicInteger.compareAndSet(/*0*/1, 2);

        System.out.println(integerFlag);


    }


}
