package com.opay.offline.component.monitor.model;

import lombok.Data;
import org.apache.ibatis.mapping.SqlCommandType;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 纯净的消息载体，用于在 Handler 链中传递
 */
@Data
public class CapturedSqlInfo implements Serializable {
    private static final long serialVersionUID = 1L;

    // --- 基础元数据 ---
    private String entityClassName;
    private String mapperMethod;
    private SqlCommandType sqlCommandType;
    private LocalDateTime timestamp = LocalDateTime.now();

    // --- SQL 数据 ---
    private String rawSql;          // 原始 SQL (带 ?)
    private String executableSql;   // 可执行 SQL (参数已填充)

    // --- 参数详情 ---
    // 有序 Map，存储所有解析到的参数
    private LinkedHashMap<String, Object> params;
    // 有序 Map，专存 WHERE 条件参数 (用于审计)
    private LinkedHashMap<String, Object> whereParams;

    // --- 执行结果 ---
    private boolean success;
    private long durationMillis;
    //执行结果，如过结果集数量超过阈值，则只取第一条
    private Object result;
    // 结果摘要 (如 "over_5" 或 执行结果)
    private String ResultSummary;

    // --- ✅ 新增：扩展槽 (Context) ---
    // 用于多个 Handler 之间传递中间结果 (例如：敏感词命中标记、风险评分等)
    private Map<String, Object> ext = new HashMap<>();

    /**
     * 链式调用辅助方法：设置扩展属性
     */
    public void addExt(String key, Object value) {
        this.ext.put(key, value);
    }

    /**
     * 链式调用辅助方法：获取扩展属性
     */
    @SuppressWarnings("unchecked")
    public <T> T getExt(String key) {
        return (T) this.ext.get(key);
    }
}