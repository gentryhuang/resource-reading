package com.code.spi.impl;

import com.code.spi.Command;

/**
 * ShutdownCommand
 *
 * desc：
 */
public class ShutdownCommand implements Command {
    @Override
    public void execute() {
        System.out.println("shutdown....");
    }
}
