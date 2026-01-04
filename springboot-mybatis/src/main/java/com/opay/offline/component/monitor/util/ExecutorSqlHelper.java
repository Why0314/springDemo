package com.opay.offline.component.monitor.util;


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
    public static void setParams(MappedStatement ms, BoundSql boundSql, Object parameterObject, CapturedSqlInfo info) {
        if (boundSql == null) return;

        List<ParameterMapping> pms = boundSql.getParameterMappings();
        if (pms == null || pms.isEmpty()) return;

        Configuration configuration = ms.getConfiguration();
        MetaObject metaObject = parameterObject == null ? null : configuration.newMetaObject(parameterObject);
        TypeHandlerRegistry typeHandlerRegistry = configuration.getTypeHandlerRegistry();

        LinkedHashMap<String, Object> allParams = new LinkedHashMap<>();
        LinkedHashMap<String, Object> whereParams = new LinkedHashMap<>();

        int idx = 0;
        for (ParameterMapping pm : pms) {
            if (pm.getMode() == ParameterMode.OUT) continue;

            String property = pm.getProperty();
            Object value = null;

            if (boundSql.hasAdditionalParameter(property)) {
                value = boundSql.getAdditionalParameter(property);
            } else if (parameterObject == null) {
                value = null;
            } else if (typeHandlerRegistry.hasTypeHandler(parameterObject.getClass())) {
                value = parameterObject;
            } else {
                value = metaObject != null ? metaObject.getValue(property) : null;
            }

            allParams.put(property, value);

            // 如果是 Wrapper 或者动态 SQL 的 where 参数，key 一般是 ew.paramNameValuePairs.*
            if (property.startsWith("ew.paramNameValuePairs.") || property.startsWith("where")) {
                whereParams.put(property, value);
            }

            idx++;
        }

        info.setParams(allParams);
        info.setWhereParams(whereParams);
    }


}

