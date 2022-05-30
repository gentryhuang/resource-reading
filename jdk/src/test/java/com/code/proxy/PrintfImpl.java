package com.code.proxy;

import lombok.extern.slf4j.Slf4j;

/**
 * RentalHouseServiceImpl
 *
 * desc：
 */
@Slf4j
public class PrintfImpl implements IPrintf {

    @Override
    public void print(String message) {
        System.out.println("print: " + message);
    }
}
