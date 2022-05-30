package com.code.queue;

import lombok.Data;

import java.io.Serializable;

/**
 * PriorityQueuePractice
 *
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
