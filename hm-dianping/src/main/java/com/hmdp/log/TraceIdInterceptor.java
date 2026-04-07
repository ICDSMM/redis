package com.hmdp.log;

import cn.hutool.core.lang.UUID;
import org.slf4j.MDC;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Spring MVC 拦截器：每个进入 DispatcherServlet 的请求生成/透传 traceId，
 * 写入 MDC 与响应头，供 AOP 业务日志与异步上报时自动带出链路 ID。
 */
@Component
public class TraceIdInterceptor implements HandlerInterceptor {

    /** 请求头与响应头中的链路 ID 名称 */
    public static final String TRACE_ID_HEADER = "X-Trace-Id";

    /** 如需在 Controller 中直接取 traceId，可用 request.getAttribute(TRACE_ID_REQUEST_ATTR) */
    public static final String TRACE_ID_REQUEST_ATTR = "traceId";

    /** MDC 中与 AOP、日志上报共用的 key */
    public static final String MDC_TRACE_ID_KEY = "traceId";

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                             @NonNull Object handler) {
        String traceId = request.getHeader(TRACE_ID_HEADER);
        if (traceId == null || traceId.trim().isEmpty()) {
            traceId = UUID.randomUUID().toString(true);
        }
        MDC.put(MDC_TRACE_ID_KEY, traceId);
        request.setAttribute(TRACE_ID_REQUEST_ATTR, traceId);
        response.setHeader(TRACE_ID_HEADER, traceId);
        return true;
    }

    @Override
    public void afterCompletion(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                                @NonNull Object handler, @Nullable Exception ex) {
        MDC.remove(MDC_TRACE_ID_KEY);
    }
}
