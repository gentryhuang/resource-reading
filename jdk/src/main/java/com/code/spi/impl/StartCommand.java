package com.code.spi.impl;

import com.code.spi.Command;

/**
 * StartCommand
 *
 * desc：
 */
public class StartCommand implements Command {

    @Override
    public void execute() {
        System.out.println("start....");
    }

}
