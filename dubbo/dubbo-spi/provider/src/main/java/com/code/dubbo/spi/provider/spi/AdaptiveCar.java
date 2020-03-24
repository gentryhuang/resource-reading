package com.code.dubbo.spi.provider.spi;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.extension.Adaptive;

/**
 * AdaptiveCar  @Adaptive注释在类上，这个类就是一个装饰类
 *
 * @author shunhua
 * @date 2019-10-09
 */
//@Adaptive
public class AdaptiveCar implements ICarService {

    @Override
    public void getColor(URL url) {

    }

    @Override
    public void getColor() {

    }

    @Adaptive
    public void testAdaptive(){
        System.out.println("test @adaptive");
    }
}