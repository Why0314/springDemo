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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
public class ExecutorSqlHelper {

    public static String buildExecutableSql(Configuration cfg,
                                            MappedStatement ms,
                                            Object param) {

        BoundSql boundSql = ms.getBoundSql(param);
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

            // ✅ 1. foreach / batch / wrapper
            if (boundSql.hasAdditionalParameter(property)) {
                value = boundSql.getAdditionalParameter(property);
            }
            // ✅ 2. 基础类型
            else if (param != null && registry.hasTypeHandler(param.getClass())) {
                value = param;
            }
            // ✅ 3. 普通对象 / @Param
            else if (metaObject != null && metaObject.hasGetter(property)) {
                value = metaObject.getValue(property);
            }
            // ✅ 4. 嵌套属性 city.provinceId
            else {
                try {
                    value = cfg.newMetaObject(param).getValue(property);
                } catch (Exception ignore) {
                }
            }

            sql = sql.replaceFirst("\\?", formatValue(value));
        }

        return sql;
    }

    private static String normalizeSql(String sql) {
        return sql.replaceAll("\\s+", " ").trim();
    }

    private static String formatValue(Object value) {
        if (value == null) return "NULL";
        if (value instanceof Number) return value.toString();
        if (value instanceof Boolean) return ((Boolean) value) ? "1" : "0";
        return "'" + value.toString().replace("'", "''") + "'";
    }


    public static Class<?> getEntityClass(String mapperId) {
        try {
            String namespace = mapperId.substring(0, mapperId.lastIndexOf('.'));
            Class<?> mapperClass = Class.forName(namespace);
            Type[] types = mapperClass.getGenericInterfaces();
            for (Type t : types) {
                String typeName = t.getTypeName();
                if (typeName.contains("<") && typeName.contains(">"))
                    return Class.forName(typeName.substring(typeName.indexOf('<') + 1, typeName.indexOf('>')));
            }
        } catch (Exception e) {
            log.error("无法解析实体类", e);
        }
        return null;
    }
//    public static void setParams(MappedStatement ms, BoundSql boundSql, Object parameterObject, CapturedSqlInfo info) {
//        if (boundSql == null) return;
//
//        List<ParameterMapping> pms = boundSql.getParameterMappings();
//        if (pms == null || pms.isEmpty()) return;
//
//        Configuration configuration = ms.getConfiguration();
//        MetaObject metaObject = parameterObject == null ? null : configuration.newMetaObject(parameterObject);
//        TypeHandlerRegistry typeHandlerRegistry = configuration.getTypeHandlerRegistry();
//
//        LinkedHashMap<String, Object> allParams = new LinkedHashMap<>();
//        LinkedHashMap<String, Object> whereParams = new LinkedHashMap<>();
//
//        int idx = 0;
//        for (ParameterMapping pm : pms) {
//            if (pm.getMode() == ParameterMode.OUT) continue;
//
//            String property = pm.getProperty();
//            Object value = null;
//
//            if (boundSql.hasAdditionalParameter(property)) {
//                value = boundSql.getAdditionalParameter(property);
//            } else if (parameterObject == null) {
//                value = null;
//            } else if (typeHandlerRegistry.hasTypeHandler(parameterObject.getClass())) {
//                value = parameterObject;
//            } else {
//                value = metaObject != null ? metaObject.getValue(property) : null;
//            }
//
//            allParams.put(property, value);
//
//            // 如果是 Wrapper 或者动态 SQL 的 where 参数，key 一般是 ew.paramNameValuePairs.*
//            if (property.startsWith("ew.paramNameValuePairs.") || property.startsWith("where")) {
//                whereParams.put(property, value);
//            }
//
//            idx++;
//        }
//
//        info.setParams(allParams);
//        info.setWhereParams(whereParams);
//    }

    public static void setParams(MappedStatement ms, BoundSql boundSql, Object parameterObject, CapturedSqlInfo info) {
        if (boundSql == null) return;

        List<ParameterMapping> pms = boundSql.getParameterMappings();
        if (pms == null || pms.isEmpty()) return;

        Configuration configuration = ms.getConfiguration();
        TypeHandlerRegistry typeHandlerRegistry = configuration.getTypeHandlerRegistry();
        MetaObject metaObject = parameterObject == null ? null : configuration.newMetaObject(parameterObject);

        LinkedHashMap<String, Object> allParams = new LinkedHashMap<>();
        LinkedHashMap<String, Object> whereParams = new LinkedHashMap<>();

        // 1. 尝试获取 MP 的 TableInfo (用于将列名转回属性名)
        // 优先从 info 中获取实体类名（假设你之前的逻辑已经填充了 entityClassName）
        Class<?> entityClass = null;
        try {
            if (info.getEntityClassName() != null) {
                entityClass = Class.forName(info.getEntityClassName());
            } else if (parameterObject != null && !typeHandlerRegistry.hasTypeHandler(parameterObject.getClass())) {
                // 尝试从 parameterObject 获取，排除 Map 等基础类型
                entityClass = parameterObject.getClass();
            }
        } catch (Exception ignored) {
        }

        TableInfo tableInfo = (entityClass != null) ? TableInfoHelper.getTableInfo(entityClass) : null;

        // 2. 预解析 SQL，找到每个 ? 对应的列名，以及 WHERE 子句的位置
        String sql = boundSql.getSql();
        // 简单的 SQL 解析：提取 ? 前面的单词作为列名
        List<String> sqlColumns = extractColumnsFromSql(sql);
        // 找到 WHERE 关键字的位置 (简单处理，取第一个 WHERE，对于子查询可能不准，但在日志场景够用了)
        int whereIndex = sql.toUpperCase().indexOf("WHERE");
        // 记录当前 ? 在 SQL 中的字符索引位置
        List<Integer> paramIndices = extractParamIndices(sql);

        for (int i = 0; i < pms.size(); i++) {
            ParameterMapping pm = pms.get(i);
            if (pm.getMode() == ParameterMode.OUT) continue;

            String propertyName = pm.getProperty();
            Object value;

            // --- 取值逻辑 (保持不变) ---
            if (boundSql.hasAdditionalParameter(propertyName)) {
                value = boundSql.getAdditionalParameter(propertyName);
            } else if (parameterObject == null) {
                value = null;
            } else if (typeHandlerRegistry.hasTypeHandler(parameterObject.getClass())) {
                value = parameterObject;
            } else {
                value = metaObject == null ? null : metaObject.getValue(propertyName);
            }

            // --- 核心修复：确定 Key 的名称 ---
            String finalKey = propertyName;

            // 如果解析到了列名，并且能找到对应的 TableInfo，尝试转回 Java 属性名
            if (i < sqlColumns.size()) {
                String columnName = sqlColumns.get(i);
                if (columnName != null && !columnName.isEmpty()) {
                    // 1. 优先尝试用 MP 映射: column -> property
                    if (tableInfo != null) {
                        // MP 的 getProperty 是通过列名找属性名，注意 MP 可能会忽略大小写
                        // 这里做一个简单的遍历查找，因为 MP API 没有直接通过 column 找 property 的公开高效方法
                        String mappedProperty = tableInfo.getFieldList().stream()
                                .filter(f -> f.getColumn().equalsIgnoreCase(columnName))
                                .map(f -> f.getProperty())
                                .findFirst()
                                .orElse(null);

                        if (mappedProperty != null) {
                            finalKey = mappedProperty;
                        } else if (tableInfo.getKeyColumn() != null && tableInfo.getKeyColumn().equalsIgnoreCase(columnName)) {
                            finalKey = tableInfo.getKeyProperty();
                        } else {
                            // 没映射上，就用列名 (比 MPGENVAL 好看)
                            finalKey = columnName;
                        }
                    } else {
                        // 没有实体信息，直接用 SQL 里的列名 (比 MPGENVAL 好看)
                        finalKey = columnName;
                    }
                }
            }

            // 加上索引前缀，防止同名参数覆盖 (如 update set name=? where name=?)
            String mapKey = i + "_" + finalKey;

            allParams.put(mapKey, value);

            // --- 核心修复：确定是否为 Where 参数 ---
            // 逻辑：如果当前 ? 的位置在 WHERE 关键字之后，则认为是条件参数
            if (whereIndex > -1 && i < paramIndices.size()) {
                if (paramIndices.get(i) > whereIndex) {
                    whereParams.put(mapKey, value);
                }
            }
        }

        info.setParams(allParams);
        info.setWhereParams(whereParams);
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

