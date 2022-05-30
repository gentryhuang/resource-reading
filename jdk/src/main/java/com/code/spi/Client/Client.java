package com.code.spi.Client;

import com.code.spi.Command;
import com.code.spi.impl.ShutdownCommand;
import com.code.spi.impl.StartCommand;

import java.util.ServiceLoader;

/**
 * Client
 *
 * desc：
 */
public class Client {
    public static void main(String[] args) {

        // ServiceLoader是JDK的，用来加载接口的实现类（通过配置文件加载）
        ServiceLoader<Command> serviceLoader = ServiceLoader.load(Command.class);
        for (Command command : serviceLoader) {
            if (command instanceof StartCommand) {
                System.out.println("判断出是Start：" + command.getClass().getSimpleName());
            }
            if (command instanceof ShutdownCommand) {
                System.out.println("判断出是Shutdown：" + command.getClass().getSimpleName());
            }
            command.execute();
        }

    }
}
