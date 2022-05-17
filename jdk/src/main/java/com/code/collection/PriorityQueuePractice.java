package com.code.collection;

import lombok.Data;

import java.io.Serializable;
import java.util.PriorityQueue;

/**
 * PriorityQueuePractice
 *
 * @author <a href="mailto:libao.huang@yunhutech.com">shunhua</a>
 * @since 2022/05/07
 * <p>
 * desc：
 */
public class PriorityQueuePractice {

    public static void main(String[] args) {
        PriorityQueue<Integer> priorityQueue = new PriorityQueue<>();
        User user = new User();

        priorityQueue.add(1);

        User user1 = new User();
        user1.setId(2);
        priorityQueue.add(2);

        Object[] objects = priorityQueue.toArray();
        for (Object object : objects) {
            System.out.println(object);
        }

    }

    @Data
    public static class User implements Serializable {
        private Integer id;
        private String name;
    }
}
