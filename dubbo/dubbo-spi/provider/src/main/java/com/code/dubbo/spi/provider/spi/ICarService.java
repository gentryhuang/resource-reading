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
     * 1. @Adaptive 注释在方法上，dubbo会使用javassit生成一个该接口的代理类。并且这个代理对象只能调用带有@Adaptive的方法否则会报错
     * 2. dubbo在使用Javassit生成代理类的时候，会对被@Adaptive注解注释的方法进行逻辑处理
     *   （1） 会在该方法内通过URL获取@Adaptive的值对应的key即扩展点实现的标识，如果没有使用@Adaptive指定值，那么该方法内部也是直接跑异常
     *   （2）如果@Adaptive注解指定了值，那么就使用该扩展点的扩展加载器根据该值获取对应的扩展点实现，那么该方法内部使用扩展点类型的操作就由获取到的实现执行
     *
     * @param url  用来演示注入，需要通过URL总线有很多参数，像@SPI、@Adaptive注解的信息都和URL相关，如@SPI和@Adaptive设置的值URL就会把该值作为key
     */
   // @Adaptive("car")
    void getColor(URL url);

    /**
     * 获取颜色
     */
    void getColor();
}