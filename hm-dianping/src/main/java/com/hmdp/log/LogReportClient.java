package com.hmdp.log;

import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.Objects;

@Component
public class LogReportClient {

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${log-collector.endpoint}")
    private String endpoint;

    @Value("${log-collector.app-name}")
    private String appName;

    @Value("${log-collector.enabled:true}")
    private boolean enabled;

    @Async("logAsyncExecutor")
    public void report(String level, String content, String sourceIp) {
        if (!enabled) {
            return;
        }
        try {
            LogMessage message = new LogMessage();
            message.setAppName(appName);
            message.setLogLevel(level);
            message.setContent(content);
            // 与 TraceIdInterceptor 写入的 MDC 键一致，异步线程由 AsyncConfig 的 TaskDecorator 复制 MDC
            message.setTraceId(
                    MDC.get(TraceIdInterceptor.MDC_TRACE_ID_KEY) != null ? MDC.get(TraceIdInterceptor.MDC_TRACE_ID_KEY)
                            : "");
            message.setSourceIp(sourceIp);
            message.setCreatedAt(LocalDateTime.now());
            if (endpoint == null) {
                throw new IllegalArgumentException("log-collector.endpoint");
            }
            restTemplate.postForEntity(endpoint,
                    new HttpEntity<>(message), String.class);
        } catch (Exception ignored) {
            // 上报失败不影响主业务
        }
    }
}