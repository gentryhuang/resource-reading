package com.code.dubbo.spi.provider.filter;


import org.apache.dubbo.rpc.*;


/**
 */
public class DubboExceptionFilter implements Filter {


    public Result invoke(Invoker<?> invoker, Invocation invocation) throws RpcException {
        Result result;
        try {
            result = invoker.invoke(invocation);
            return result;
        } catch (Exception e) {
            result = new AppResponse();
        }
        return result;
    }


}
