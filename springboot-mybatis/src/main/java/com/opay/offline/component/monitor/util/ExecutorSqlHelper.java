package com.opay.offline.component.monitor.util;


import com.alibaba.druid.sql.SQLUtils;
import com.alibaba.druid.sql.ast.SQLExpr;
import com.alibaba.druid.sql.ast.SQLStatement;
import com.alibaba.druid.sql.ast.statement.SQLSelect;
import com.alibaba.druid.sql.ast.statement.SQLSelectQueryBlock;
import com.alibaba.druid.sql.ast.statement.SQLSelectStatement;
import com.alibaba.druid.sql.ast.statement.SQLUpdateStatement;
import com.alibaba.druid.util.JdbcConstants;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.mapping.ParameterMode;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.type.TypeHandlerRegistry;

import java.lang.reflect.Type;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;

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
                    } catch (Exception ignored) {
                    }
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
                    if (className.contains("<")) className = className.substring(0, className.indexOf('<'));
                    return Class.forName(className);
                }
            }
        } catch (Exception e) {
            // 仅打印一次 debug，防止日志泛滥
            log.debug("无法解析 Mapper 对应的实体类: {}", mapperId);
        }
        return null;
    }

    public static String convertUpdateToSelect(String updateSql, String targetColumns) {
        try {
            // 1. 解析原始 SQL
            List<SQLStatement> statements = SQLUtils.parseStatements(updateSql, JdbcConstants.MYSQL);
            if (statements.isEmpty() || !(statements.get(0) instanceof SQLUpdateStatement)) {
                log.warn("SQL转换失败: 不是有效的 UPDATE 语句");
                return null;
            }

            SQLUpdateStatement updateStmt = (SQLUpdateStatement) statements.get(0);

            // 2. 创建新的 SELECT 结构
            SQLSelectStatement selectStmt = new SQLSelectStatement();
            SQLSelect select = new SQLSelect();
            SQLSelectQueryBlock queryBlock = new SQLSelectQueryBlock();

            // 3. 设置 SELECT 字段 (targetColumns)
            // 使用 SQLUtils.parseExpr 将字符串字段名转为 AST 表达式
            // 注意：如果 targetColumns 包含多个字段用逗号分隔，这里做简单处理，建议传入单个或自行 split
            String[] cols = targetColumns.split(",");
            for (String col : cols) {
                queryBlock.addSelectItem(SQLUtils.toSQLExpr(col.trim()));
            }

            // 4. 迁移 FROM (从 Update 语句获取表名)
            queryBlock.setFrom(updateStmt.getTableSource());

            // 5. 迁移 WHERE (核心：直接复用 Update 的 Where 条件)
            SQLExpr where = updateStmt.getWhere();
            if (where != null) {
                queryBlock.setWhere(where);
            }

            // 6. 组装并生成 SQL
            select.setQuery(queryBlock);
            selectStmt.setSelect(select);

            return SQLUtils.toSQLString(selectStmt, JdbcConstants.MYSQL);

        } catch (Exception e) {
            log.error("SQL转换异常, sql: {}", updateSql, e);
            return null;
        }
    }

}

