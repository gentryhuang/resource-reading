package com.code.concurrent;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

/**
 * Fruit
 *
 * desc：
 */
@Data
@Slf4j
public class Fruit {
    /**
     * 名称
     */
    private String name;

    public Fruit(String name) {
        this.name = name;
    }

    @Override
    protected void finalize() throws Throwable {
        super.finalize();
        log.info(name + "finalize !");
    }

    @Override
    public String toString() {
        return "Fruit{" +
                "name='" + name + '\'' +
                '}';
    }
}
