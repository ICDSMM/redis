package com.hmdp.log;

import java.time.LocalDateTime;

public class LogMessage {
    private String appName;
    private String logLevel;
    private String content;
    private String traceId;
    private String sourceIp;
    private LocalDateTime createdAt;

    public String getAppName() { return appName; }
    public void setAppName(String appName) { this.appName = appName; }

    public String getLogLevel() { return logLevel; }
    public void setLogLevel(String logLevel) { this.logLevel = logLevel; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getTraceId() { return traceId; }
    public void setTraceId(String traceId) { this.traceId = traceId; }

    public String getSourceIp() { return sourceIp; }
    public void setSourceIp(String sourceIp) { this.sourceIp = sourceIp; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}