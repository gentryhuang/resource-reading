package com.code.concurrent;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

/**
 * Fruit
 *
 * @author <a href="mailto:libao.huang@yunhutech.com">shunhua</a>
 * @since 2021/08/26
 * <p>
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
