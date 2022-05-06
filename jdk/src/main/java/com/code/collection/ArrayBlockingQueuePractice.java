package com.code.collection;

import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.ArrayBlockingQueue;

/**
 * ArrayBlockingQueuePractice
 *
 * @author <a href="mailto:libao.huang@yunhutech.com">shunhua</a>
 * @since 2022/05/05
 * <p>
 * desc：
 */
public class ArrayBlockingQueuePractice {
    public static void main(String[] args) throws InterruptedException {
        Collection<Integer> collection = new ArrayList<>();
        collection.add(1);
        collection.add(2);
        collection.add(3);

        ArrayBlockingQueue<Integer> arrayBlockingQueue = new ArrayBlockingQueue<>(5,true,collection);

        // 在不超出队列长度的情况下插入元素，可以立即执行，成功返回true，如果队列满了就抛出异常,其底层实现的是offer方法，不会阻塞。
        arrayBlockingQueue.add(1);

        // 在不超出队列长度的情况下插入元素的时候则可以立即在队列的尾部插入指定元素,成功时返回true，如果此队列已满，则返回false。不会阻塞。
        arrayBlockingQueue.offer(2);


        //  插入元素的时候，如果队列满了就进行等待，直到队列可用。
        arrayBlockingQueue.put(3);

        //------------分割线----------/

        // 底层是用到了poll()方法，检索并且删除返回队列头的元素,与poll()方法不同的是，元素没有是进行抛异常NoSuchElementException。
        Integer remove = arrayBlockingQueue.remove();
        System.out.println(remove);

        // 检索并且删除返回队列头的元素,有就返回没有就返回null。
        Integer poll = arrayBlockingQueue.poll();
        System.out.println(poll);

        // 检索并且删除返回队列头的元素,如果元素没有会一直等待，有就返回。
        Integer take = arrayBlockingQueue.take();
        System.out.println(take);


        // 检索但不移除此队列的头部;如果此队列为空，则返回null。返回头部元素。
        Integer peek = arrayBlockingQueue.peek();
        System.out.println(peek);



    }
}
