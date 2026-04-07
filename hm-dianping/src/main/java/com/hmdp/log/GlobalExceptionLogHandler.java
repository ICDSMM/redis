package com.hmdp.log;

import com.hmdp.dto.Result;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import javax.servlet.http.HttpServletRequest;

@RestControllerAdvice
public class GlobalExceptionLogHandler {

    private final LogReportClient logReportClient;

    public GlobalExceptionLogHandler(LogReportClient logReportClient) {
        this.logReportClient = logReportClient;
    }

    @ExceptionHandler(Exception.class)
    public Result handle(Exception e, HttpServletRequest request) {
        String content = "全局异常捕获: " + e.getMessage();
        logReportClient.report("ERROR", content, request.getRemoteAddr());
        return Result.fail("系统异常，请稍后重试");
    }
}