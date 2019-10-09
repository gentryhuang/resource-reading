package com.code.dubbo.spi.provider.spi;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.extension.Adaptive;

/**
 * AdaptiveCar   是一个装饰类
 *
 * @author shunhua
 * @date 2019-10-09
 */
@Adaptive
public class AdaptiveCar implements ICarService {

    @Override
    public void getColor(URL url) {

    }

    @Override
    public void getColor() {

    }

    /**
     * @Adaptive 注释在方法上，dubbo会使用javassit生成一个该类的代理类。并且这个代理对象只能调用带有@Adaptive的方法
     * 否则会报错
     */
    @Adaptive
    public void testAdaptive(){
        System.out.println("test @adaptive");
    }
}