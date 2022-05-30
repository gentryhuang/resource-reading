package com.code.proxy;

import org.junit.Test;

import java.io.IOException;

/**
 * ProxyTest
 *
 * desc：
 */
public class ProxyTest {

    @Test
    public void test() throws IOException {
        IPrintf proxy = Client.getProxy();
        proxy.print("hello world!");
    }
}
