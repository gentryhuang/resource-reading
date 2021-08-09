package com.code.proxy;

import lombok.extern.slf4j.Slf4j;

/**
 * RentalHouseServiceImpl
 *
 * @author <a href="mailto:libao.huang@yunhutech.com">shunhua</a>
 * @since 2021/01/05
 * <p>
 * desc：
 */
@Slf4j
public class PrintfImpl implements IPrintf {

    @Override
    public void print(String message) {
        System.out.println("print: " + message);
    }
}
