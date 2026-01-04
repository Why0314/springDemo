package com.opay.offline.component.monitor.dto;

import lombok.Data;
import org.apache.ibatis.mapping.SqlCommandType;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;

@Data
public class CapturedSqlInfo {
    private String entityClassName;       // 实体类名
    private String tableName;       // 数据库表名
    private String mapperMethod;          // Mapper 方法
    private String rawSql;                // 原始 SQL
    private SqlCommandType sqlCommandType;
    private LinkedHashMap<String, Object> params;      // 全部参数
    private LinkedHashMap<String, Object> whereParams; // 条件参数
    private String executableSql;         // 可执行 SQL
    private Object result;                // 执行结果
    private boolean success;              // SQL 是否成功
    private LocalDateTime timestamp = LocalDateTime.now();
    private long durationMillis; // SQL 执行耗时（大致）

}
