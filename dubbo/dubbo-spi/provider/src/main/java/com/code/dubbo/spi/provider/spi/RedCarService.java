package com.code.dubbo.spi.provider.spi;

import org.apache.dubbo.common.URL;

/**
 * RedCarService
 *
 * @author shunhua
 * @date 2019-10-09
 */
public class RedCarService implements ICarService {

    @Override
    public void getColor(URL url) {
        System.out.print("红颜色的车");
    }

    @Override
    public void getColor() {
        System.out.println("红颜色的车");
    }
}