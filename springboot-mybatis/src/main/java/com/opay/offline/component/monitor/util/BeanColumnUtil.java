package com.opay.offline.component.monitor.util;

import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 数据库字段 (snake_case) 与 Java Bean 属性 (camelCase) 转换工具
 * <p>
 * 特性：
 * 1. 高性能：内部使用 ConcurrentHashMap 缓存转换结果，避免重复计算
 * 2. 线程安全：适合高并发环境
 * 3. 零依赖：纯 Java 实现，不依赖 Guava 或 Commons-Lang
 */
@Slf4j
public class BeanColumnUtil {

    // 缓存：snake_case -> camelCase
    private static final Map<String, String> SNAKE_TO_CAMEL_CACHE = new ConcurrentHashMap<>(1024);

    // 缓存：camelCase -> snake_case
    private static final Map<String, String> CAMEL_TO_SNAKE_CACHE = new ConcurrentHashMap<>(1024);

    /**
     * 下划线转驼峰 (user_name -> userName)
     *
     * @param snakeCaseStr 数据库字段名
     * @return Java 属性名
     */
    public static String toCamelCase(String snakeCaseStr) {
        if (snakeCaseStr == null || snakeCaseStr.isEmpty()) {
            return snakeCaseStr;
        }
        // 优先读缓存
        return SNAKE_TO_CAMEL_CACHE.computeIfAbsent(snakeCaseStr, k -> {
            StringBuilder sb = new StringBuilder();
            boolean nextUpperCase = false;

            for (int i = 0; i < k.length(); i++) {
                char c = k.charAt(i);
                if (c == '_') {
                    nextUpperCase = true;
                } else {
                    if (nextUpperCase) {
                        sb.append(Character.toUpperCase(c));
                        nextUpperCase = false;
                    } else {
                        // 默认转小写，防止数据库字段全是 USER_NAME 这种情况
                        sb.append(Character.toLowerCase(c));
                    }
                }
            }
            return sb.toString();
        });
    }

    /**
     * 驼峰转下划线 (userName -> user_name)
     *
     * @param camelCaseStr Java 属性名
     * @return 数据库字段名
     */
    public static String toSnakeCase(String camelCaseStr) {
        if (camelCaseStr == null || camelCaseStr.isEmpty()) {
            return camelCaseStr;
        }
        // 优先读缓存
        return CAMEL_TO_SNAKE_CACHE.computeIfAbsent(camelCaseStr, k -> {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < k.length(); i++) {
                char c = k.charAt(i);
                if (Character.isUpperCase(c)) {
                    // 遇到大写，前面加下划线，并将该字符转小写
                    if (i > 0) {
                        sb.append('_');
                    }
                    sb.append(Character.toLowerCase(c));
                } else {
                    sb.append(c);
                }
            }
            return sb.toString();
        });
    }

    /**
     * 批量转换 Map 的 Key (通常用于将数据库查询结果 List<Map> 转为前端友好的驼峰格式)
     *
     * @param originMap Key 为下划线的 Map
     * @return Key 为驼峰的 Map (LinkedHashMap 保持顺序)
     */
    public static Map<String, Object> mapKeysToCamel(Map<String, Object> originMap) {
        if (originMap == null) return null;

        // 使用 LinkedHashMap 保持 SQL 查询结果的列顺序
        Map<String, Object> newMap = new java.util.LinkedHashMap<>(originMap.size());

        for (Map.Entry<String, Object> entry : originMap.entrySet()) {
            String camelKey = toCamelCase(entry.getKey());
            newMap.put(camelKey, entry.getValue());
        }
        return newMap;
    }
}