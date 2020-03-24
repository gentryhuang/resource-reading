package com.code.dubbo.spi.provider.spi;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.extension.ExtensionLoader;

import java.util.HashMap;
import java.util.Map;

/**
 * SpiDemo
 *
 * @author shunhua
 * @date 2019-10-09
 */
public class SpiDemo {
    public static void main(String[] args) {

        // 获取ICarService扩展点对应的扩展加载器
        ExtensionLoader<ICarService> extensionLoader = ExtensionLoader.getExtensionLoader(ICarService.class);

        // 通过扩展加载器获取扩展点的某个实现
        ICarService benz = extensionLoader.getExtension("benz");

        // 验证包装类，需要把配置中wrapper打开
        ICarService red = extensionLoader.getExtension("red");
        red.getColor();


        // 通过设置URL，来改变某种行为，这里是完成IOC功能，即通过Setter把需要的实现进行注入，这里注入的就是red对应的实现
        Map<String, String> map = new HashMap<>(2);
        // 指定URL中key-valu参数
        map.put("car", "red");
        URL url = new URL("", "", 1, map);
        // 调用benz实现的getColor方法
        benz.getColor(url);
    }
}