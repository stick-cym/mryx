package com.person.mryx.common.exception;

import com.person.mryx.common.result.Result;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;

@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(Exception.class)
    @ResponseBody
    public Result<Object> error(Exception e){
        e.printStackTrace();
        return Result.fail(null);
    }

    //自定义异常处理
    @ExceptionHandler(MryxException.class)
    @ResponseBody
    public Result error(MryxException exception) {
        return Result.build(null,exception.getCode(),exception.getMessage());
    }
}
