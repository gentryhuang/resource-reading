package com.code.dubbo.spi.provider.spi;

import org.apache.dubbo.common.URL;

/**
 * CarWrapper
 *
 * @author shunhua
 * @date 2019-10-09
 */
public class CarWrapper2 implements ICarService {


    private ICarService carService;

    public CarWrapper2(ICarService carService){
        this.carService = carService;
    }

    @Override
    public void getColor(URL url) {

    }

    @Override
    public void getColor() {
        System.out.println("Wrapper2...start");
        carService.getColor();
        System.out.println("Wrapper2...end");
    }
}