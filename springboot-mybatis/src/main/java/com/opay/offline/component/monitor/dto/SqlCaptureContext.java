package com.opay.offline.component.monitor.dto;

import lombok.Data;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.session.Configuration;

/**
 * 内部使用的上下文对象
 * 仅在 "拦截器 -> 异步解析" 阶段存活，解析完即丢弃，不传给 Handler
 */
@Data
public class SqlCaptureContext {

    // 最终要产出的 DTO
    private final CapturedSqlInfo info;

    // --- 构建期需要的 MyBatis 重型对象 ---
    private Configuration configuration;
    private MappedStatement mappedStatement;
    private Object parameterObject;
    private BoundSql boundSql;

    public SqlCaptureContext(CapturedSqlInfo info) {
        this.info = info;
    }
}