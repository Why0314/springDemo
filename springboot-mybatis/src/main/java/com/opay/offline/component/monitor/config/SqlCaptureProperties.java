package com.opay.offline.component.monitor.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "monitor.sql")
public class SqlCaptureProperties {
    // 总开关
    private boolean enabled = true;
    // 核心线程数
    private int corePoolSize = 4;
    // 最大线程数
    private int maxPoolSize = 16;
    // 队列容量 (设置大一点防止高并发丢日志，但要注意内存)
    private int queueCapacity = 2000;
    // 线程空闲存活时间 (秒)
    private int keepAliveSeconds = 60;
    // SQL 最大长度限制 (防止超大批量插入导致 OOM)
    private int maxSqlLength = 10000;
}