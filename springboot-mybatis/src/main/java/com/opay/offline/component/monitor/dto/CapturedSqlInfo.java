package com.opay.offline.component.monitor.dto;

import lombok.Data;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.session.Configuration;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;

@Data
public class CapturedSqlInfo {
    // 实体类名
    private String entityClassName;
    // 数据库表名
    private String tableName;
    // Mapper 方法
    private String mapperMethod;
    // 原始 SQL
    private String rawSql;
    private SqlCommandType sqlCommandType;
    // 全部参数
    private LinkedHashMap<String, Object> params;
    // where后面的条件参数
    private LinkedHashMap<String, Object> whereParams;
    // 可执行 SQL
    private String executableSql;
    // 执行结果
    private Object result;
    // 执行结果摘要
    private String resultSummary;
    // SQL 是否成功
    private boolean success;
    private LocalDateTime timestamp = LocalDateTime.now();
    // SQL 执行耗时
    private long durationMillis;



    // 异步处理时需要的上下文，处理完后置空以释放内存
    private transient Configuration configuration;
    private transient MappedStatement mappedStatement;
    private transient Object parameterObject;
    private transient BoundSql boundSql;

}
