package com.code.concurrent;

import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * ReadWriteLock
 *
 * desc：
 */
public class ReadWriteLock {
    ReentrantReadWriteLock reentrantReadWriteLock = new ReentrantReadWriteLock();
    ReentrantReadWriteLock.ReadLock readLock = reentrantReadWriteLock.readLock();
    ReentrantReadWriteLock.WriteLock writeLock = reentrantReadWriteLock.writeLock();

    public String cacheData = "DATA";
    public volatile Boolean updateFlag = Boolean.FALSE;


    /**
     * 读取缓存，一旦缓存被修改破坏，需要重新计算
     */
    private void processData() {

        // 获取读锁，该方法主要是读取缓存数据
        readLock.lock();

        // 共享数据发生改变，需要重新计算缓存数据
        if (updateFlag) {
            // 必须先释放掉读锁，后续加写锁更新缓存
            readLock.unlock();

            // 1 获取写锁，用于只有一个线程更新缓存
            writeLock.lock();

            try {
                if (updateFlag) {
                    // 重新计算缓存值
                    cacheData = caculateCacheData();
                    updateFlag = Boolean.FALSE;
                }

                // 2 获取读锁
                readLock.lock();
            } finally {
                // 3 释放写锁
                writeLock.unlock();
            }

            // 以上 1、2、3 步完成锁降级，即写锁降级为读锁
        }

        try {
            // 使用缓存
            System.out.println("print cache: " + cacheData);
        } finally {
            readLock.unlock();
        }
    }

    private String caculateCacheData() {
        return "";
    }


}
