package com.code.dubbo.spi.provider.spi;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.extension.Adaptive;
import org.apache.dubbo.common.extension.SPI;

/**
 * ICarService
 *
 * @author shunhua
 * @date 2019-10-09
 */
@SPI
public interface ICarService {

    /**
     *
     * @param url  用来演示注入，需要通过URL总线
     */
    @Adaptive("car")
    void getColor(URL url);

    /**
     * 获取颜色
     */
    void getColor();
}