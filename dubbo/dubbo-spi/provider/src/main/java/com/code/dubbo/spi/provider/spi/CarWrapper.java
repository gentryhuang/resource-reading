package com.code.dubbo.spi.provider.spi;

import org.apache.dubbo.common.URL;

/**
 * CarWrapper
 *
 * @author shunhua
 * @date 2019-10-09
 */
public class CarWrapper implements ICarService {


    private ICarService carService;

    /**
     * AOP实现 只要扩展点的实现类的一个参数的有参构造方法的参数类型是扩展点类型，通过构造方法给扩展点类型赋值。
     * 那么这样的类就是Wrapper类，这个就是实现AOP本质。
     *
     * @param carService
     */
    public CarWrapper(ICarService carService){
        this.carService = carService;
    }

    @Override
    public void getColor(URL url) {

    }

    @Override
    public void getColor() {
        System.out.println("Wrapper...start");
        carService.getColor();
        System.out.println("Wrapper...end");
    }
}