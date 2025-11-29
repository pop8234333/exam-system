package com.zmh.exam.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * @author zmh
 * @since 2025/11/25 15:43<br/>
 * -----------------------------<br/>
 * Project    : exam-system<br/>
 * Package    : com.zmh.exam.common<br/>
 * ClassName  : GlobalExceptionHandler<br/>
 * Description:  请填写类的描述
 */
@Slf4j
@RestControllerAdvice//统一异常处理
public class GlobalExceptionHandler {

    @ExceptionHandler(value = Exception.class)
    public Result<Exception> handleException(Exception e) {
        //记录异常日志
        log.error("服务器发生运行时异常！根本原因为:{},异常信息为：{}",e.getMessage(),getRootCause(e).getMessage(),e);
        return Result.error("服务器发生运行时异常！: %s".formatted(e.getMessage()));
    }
    // 获取异常的根本原因
    private static Throwable getRootCause(Throwable throwable) {
        Throwable cause = throwable.getCause();
        while (cause != null && cause != throwable) {
            throwable = cause;
            cause = throwable.getCause();
        }
        return throwable;
    }
}
