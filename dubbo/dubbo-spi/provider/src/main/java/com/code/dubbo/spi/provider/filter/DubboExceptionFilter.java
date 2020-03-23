package com.code.dubbo.spi.provider.filter;


import org.apache.dubbo.common.extension.Activate;
import org.apache.dubbo.rpc.*;

import static org.apache.dubbo.common.constants.CommonConstants.PROVIDER;


/**
 */
@Activate(group = PROVIDER)
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
