package com.opay.offline.component.monitor.util;


import com.baomidou.mybatisplus.core.metadata.TableInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.opay.offline.component.monitor.dto.CapturedSqlInfo;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.mapping.ParameterMode;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.type.TypeHandlerRegistry;

import java.lang.reflect.Type;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
public class ExecutorSqlHelper {
    private static final Map<String, Class<?>> MAPPER_ENTITY_CACHE = new ConcurrentHashMap<>(256);
    private static final Class<?> NO_ENTITY_MARKER = Void.class; // 标记无实体的Mapper，防止缓存穿透

    /**
     * 只有在异步线程中才调用此方法进行复杂的SQL拼装
     */
    public static String buildExecutableSql(Configuration cfg, BoundSql boundSql, Object param) {
        try {
            String sql = normalizeSql(boundSql.getSql());
            List<ParameterMapping> pms = boundSql.getParameterMappings();
            if (pms == null || pms.isEmpty()) {
                return sql;
            }

            MetaObject metaObject = param == null ? null : cfg.newMetaObject(param);
            TypeHandlerRegistry registry = cfg.getTypeHandlerRegistry();

            for (ParameterMapping pm : pms) {
                if (pm.getMode() == ParameterMode.OUT) continue;

                String property = pm.getProperty();
                Object value = null;

                if (boundSql.hasAdditionalParameter(property)) {
                    value = boundSql.getAdditionalParameter(property);
                } else if (param != null && registry.hasTypeHandler(param.getClass())) {
                    value = param;
                } else if (metaObject != null && metaObject.hasGetter(property)) {
                    value = metaObject.getValue(property);
                } else {
                    // 尝试从 Configuration 的 MetaObject 获取（处理嵌套等复杂情况）
                    try {
                        if (param != null) {
                            value = cfg.newMetaObject(param).getValue(property);
                        }
                    } catch (Exception ignored) {}
                }
                // 使用 Matcher.quoteReplacement 防止 value 中包含 $ \ 等特殊字符导致报错
                sql = sql.replaceFirst("\\?", Matcher.quoteReplacement(formatValue(value)));
            }
            return sql;
        } catch (Exception e) {
            log.warn("SQL assembly failed", e);
            return "SQL_ASSEMBLY_ERROR";
        }
    }

    private static String normalizeSql(String sql) {
        return sql == null ? "" : sql.replaceAll("\\s+", " ").trim();
    }

    private static String formatValue(Object value) {
        if (value == null) return "NULL";
        if (value instanceof Number) return value.toString();
        if (value instanceof Boolean) return ((Boolean) value) ? "1" : "0";
        if (value instanceof Date || value instanceof java.time.temporal.Temporal) return "'" + value.toString() + "'";
        // ✅ 优化点2：限制参数长度，防止大字符串导致内存问题
        String strVal = value.toString();
        if (strVal.length() > 500) strVal = strVal.substring(0, 500) + "...(truncated)";
        return "'" + strVal.replace("'", "''") + "'";
    }

    /**
     * 高性能获取实体类（带缓存）
     */
    public static Class<?> getEntityClass(String mapperId) {
        // 先查缓存
        Class<?> cached = MAPPER_ENTITY_CACHE.get(mapperId);
        if (cached != null) {
            return cached == NO_ENTITY_MARKER ? null : cached;
        }

        // 缓存未命中，进行解析
        Class<?> parsedClass = parseEntityClass(mapperId);

        // 存入缓存
        MAPPER_ENTITY_CACHE.put(mapperId, parsedClass == null ? NO_ENTITY_MARKER : parsedClass);
        return parsedClass;
    }

    private static Class<?> parseEntityClass(String mapperId) {
        try {
            String namespace = mapperId.substring(0, mapperId.lastIndexOf('.'));
            Class<?> mapperClass = Class.forName(namespace);
            Type[] types = mapperClass.getGenericInterfaces();
            for (Type t : types) {
                String typeName = t.getTypeName();
                // 简单的泛型解析，生产环境建议使用 Spring 的 GenericTypeResolver 或 Guava
                if (typeName.contains("<") && typeName.contains(">")) {
                    String className = typeName.substring(typeName.indexOf('<') + 1, typeName.indexOf('>'));
                    // 处理可能存在的泛型嵌套，简单取第一个
                    if(className.contains("<")) className = className.substring(0, className.indexOf('<'));
                    return Class.forName(className);
                }
            }
        } catch (Exception e) {
            // 仅打印一次 debug，防止日志泛滥
            log.debug("无法解析 Mapper 对应的实体类: {}", mapperId);
        }
        return null;
    }
    /**
     * 设置参数 Map (修复 Key 命名策略)
     * 策略：优先使用属性名，仅在 Key 冲突时追加索引
     */
    public static void setParams(MappedStatement ms, BoundSql boundSql, Object parameterObject, CapturedSqlInfo info) {
        if (boundSql == null) return;
        try {
            List<ParameterMapping> pms = boundSql.getParameterMappings();
            if (pms == null || pms.isEmpty()) return;

            Configuration configuration = ms.getConfiguration();
            TypeHandlerRegistry typeHandlerRegistry = configuration.getTypeHandlerRegistry();
            MetaObject metaObject = parameterObject == null ? null : configuration.newMetaObject(parameterObject);

            // 使用 LinkedHashMap 保持参数顺序
            LinkedHashMap<String, Object> allParams = new LinkedHashMap<>();

            for (ParameterMapping pm : pms) {
                if (pm.getMode() == ParameterMode.OUT) continue;

                String property = pm.getProperty();
                Object value;

                // 1. 取值逻辑 (保持不变)
                if (boundSql.hasAdditionalParameter(property)) {
                    value = boundSql.getAdditionalParameter(property);
                } else if (parameterObject == null) {
                    value = null;
                } else if (typeHandlerRegistry.hasTypeHandler(parameterObject.getClass())) {
                    value = parameterObject;
                } else {
                    value = metaObject != null ? metaObject.getValue(property) : null;
                }

                // 2. 值处理：大对象截断
                if (value != null && value.toString().length() > 512) {
                    value = "[Long Content Truncated...]";
                }

                // 3. ✅ 修复 Key 的生成逻辑
                // 移除 "index_" 前缀。如果 map 中已存在该 key (同名参数)，则追加 "_index" 防止覆盖
                String key = property;
                if (allParams.containsKey(key)) {
                    // 出现重复参数名 (例如 set name=? where name=?)，追加索引区分
                    // 格式变更为: name_2, name_5
                    key = property + "_" + pms.indexOf(pm);
                }

                // 针对 MyBatis Plus 的 ew.paramNameValuePairs.xxx 进行美化 (可选)
                if (key.startsWith("ew.paramNameValuePairs.")) {
                    key = key.substring("ew.paramNameValuePairs.".length());
                }

                allParams.put(key, value);
            }
            info.setParams(allParams);
        } catch (Exception e) {
            log.warn("Monitor: extract params failed", e);
        }
    }


    /**
     * 辅助方法：提取 SQL 中每个 ? 前面的列名
     * 针对: UPDATE table SET name=?, age=? WHERE id=?
     * 解析出: ["name", "age", "id"]
     */
    private static List<String> extractColumnsFromSql(String sql) {
        List<String> columns = new ArrayList<>();
        // 匹配规则：找到 ? 之前最近的一个单词。
        // 忽略 =, LIKE, <, >, !=, 以及空白字符
        // 这是一个简化的正则，能覆盖大多数标准 SQL 场景
        Pattern pattern = Pattern.compile("([a-zA-Z0-9_`]+)\\s*(=|LIKE|IN|<|>|!=|>=|<=)\\s*\\?", Pattern.CASE_INSENSITIVE);

        // 由于 Java 正则没法直接按顺序流式匹配所有 ? (因为 ? 是特殊字符且我们只关心 ? 前面的)，
        // 我们采取遍历字符串的方式更稳妥，或者简单地用 split

        // 简单实现：遍历 SQL，寻找 ?
        char[] chars = sql.toCharArray();
        StringBuilder buffer = new StringBuilder();

        // 记录上一个有效的单词
        String lastWord = "";

        for (int i = 0; i < chars.length; i++) {
            char c = chars[i];
            if (c == '?') {
                columns.add(lastWord);
            } else {
                // 简单的词法分析
                if (isValidColumnChar(c)) {
                    buffer.append(c);
                } else {
                    // 遇到非单词字符（空格、=、逗号等），如果 buffer 有值，更新 lastWord
                    if (buffer.length() > 0) {
                        lastWord = buffer.toString();
                        buffer.setLength(0);
                    }
                }
            }
        }
        return columns;
    }

    private static boolean isValidColumnChar(char c) {
        // 允许字母、数字、下划线、反引号(mysql)
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '_' || c == '`';
    }

    /**
     * 辅助方法：获取每个 ? 在 SQL 中的索引位置
     */
    private static List<Integer> extractParamIndices(String sql) {
        List<Integer> indices = new ArrayList<>();
        for (int i = 0; i < sql.length(); i++) {
            if (sql.charAt(i) == '?') {
                indices.add(i);
            }
        }
        return indices;
    }
}

