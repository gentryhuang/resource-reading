package com.code.proxy;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * com.code.mixture.Client
 *
 * @author <a href="mailto:libao.huang@yunhutech.com">shunhua</a>
 * @since 2021/01/05
 * <p>
 * desc：
 */
public class Client {
    /**
     * 实例化对象
     */
    private static final IPrintf PRINTF = new PrintfImpl();

    /**
     * JDK 生成代理对象
     *
     * @return $Proxy0
     */
    public static IPrintf getProxy() {

        // 返回 printf 对象的代理对象
        return (IPrintf) Proxy.newProxyInstance(
                // 类加载器，在程序运行时将生成的代理类加载到JVM中
                PRINTF.getClass().getClassLoader(),
                // 被代理类的所有接口信息，用来确定生成的代理类可以具有哪些方法
                PRINTF.getClass().getInterfaces(),
                // 调用处理器，每个代理对象都具有一个关联的调用处理器，用于指定代理类需要完成的具体操作。
                // 该接口中有一个 invoke 方法，代理对象调用任何目标接口的方法时都会调用该 invoke 方法，该方法中会调用目标类的目标方法。
                new InvocationHandler() {
                    /**
                     *
                     * @param proxy 代理对象
                     * @param method 代理对象当前调用的方法
                     * @param args 方法参数
                     * @return 方法执行的结果（无返回值则为 null）
                     * @throws Throwable 异常
                     */
                    @Override
                    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                        // 前置逻辑
                        System.out.println("before action ... ");
                        // 执行代理类的目标方法
                        Object result = method.invoke(PRINTF, args);
                        // 后置逻辑
                        System.out.println("after action ... ");
                        return result;
                    }
                }
        );
    }

    public static void main(String[] args) {
        IPrintf proxy = Client.getProxy();
        proxy.print("hello world!");
    }
}
