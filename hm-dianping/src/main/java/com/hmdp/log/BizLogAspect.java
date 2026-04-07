package com.hmdp.log;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletRequest;

@Aspect
@Component
public class BizLogAspect {

    private final LogReportClient logReportClient;

    public BizLogAspect(LogReportClient logReportClient) {
        this.logReportClient = logReportClient;
    }

    @Around("@annotation(bizLog)")
    public Object around(ProceedingJoinPoint pjp, BizLog bizLog) throws Throwable {
        long start = System.currentTimeMillis();
        String ip = getClientIp();
        String method = pjp.getSignature().toShortString();
        String desc = bizLog.value();

        try {
            Object result = pjp.proceed();
            long cost = System.currentTimeMillis() - start;
            String content = String.format("业务成功 | desc=%s | method=%s | cost=%dms", desc, method, cost);
            logReportClient.report("INFO", content, ip);
            return result;
        } catch (Throwable ex) {
            long cost = System.currentTimeMillis() - start;
            String content = String.format("业务异常 | desc=%s | method=%s | cost=%dms | ex=%s",
                    desc, method, cost, ex.getMessage());
            logReportClient.report("ERROR", content, ip);
            throw ex;
        }
    }

    private String getClientIp() {
        // 兼容异步线程或非Web上下文，避免Request为空导致切面异常
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes) {
            HttpServletRequest servletRequest = ((ServletRequestAttributes) attributes).getRequest();
            return servletRequest.getRemoteAddr();
        }
        return "unknown";
    }
}