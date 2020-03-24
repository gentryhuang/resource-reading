package com.code.dubbo.spi.provider.spi;

import org.apache.dubbo.common.URL;

/**
 * BenzCar
 *
 * @author shunhua
 * @date 2019-10-09
 */
public class BenzCar implements ICarService{

    private ICarService carService;

    /**
     * Dubbo只支持Setter注入,具体注入扩展点哪个具体实现需要通过URl决定
     * @param carService
     */
    public void setCarService(ICarService carService){
        this.carService = carService;
    }

    /**
     * 1. 根据URL中的参数来找方法对应的哪个实现类，然后通过调用setter方法，把该实现类注入进来
     * 2. 在getColor方法所在的接口中，已经通过@Adaptive注解指定了car这个参数，URL会判断自己有没有key为car的这个key-value键值对
     *    参数，如果有就取出对应的值，对应的值就是接口实例对应的标识即配置文件中的key，然后把拿到对应的实例，通过setter方法注入进去
     *
     * @param url  URL总线
     */
    @Override
    public void getColor(URL url) {
        System.out.print("奔驰-");
        carService.getColor(url);
    }

    @Override
    public void getColor() {
       //...
        System.out.println("普通方法");
    }
}