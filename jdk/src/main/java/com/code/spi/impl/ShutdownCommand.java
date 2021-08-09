package com.code.spi.impl;

import com.code.spi.Command;

/**
 * ShutdownCommand
 *
 * @author <a href="mailto:libao.huang@yunhutech.com">shunhua</a>
 * @since 2020/10/17
 * <p>
 * desc：
 */
public class ShutdownCommand implements Command {
    @Override
    public void execute() {
        System.out.println("shutdown....");
    }
}
