package com.code.proxy;

import org.junit.Test;

import java.io.IOException;

/**
 * ProxyTest
 *
 * @author <a href="mailto:libao.huang@yunhutech.com">shunhua</a>
 * @since 2021/01/05
 * <p>
 * desc：
 */
public class ProxyTest {

    @Test
    public void test() throws IOException {
        IPrintf proxy = Client.getProxy();
        proxy.print("hello world!");
    }
}
