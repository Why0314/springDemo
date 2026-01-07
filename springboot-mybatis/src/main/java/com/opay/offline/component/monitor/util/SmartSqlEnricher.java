package com.opay.offline.component.monitor.util;

import com.opay.offline.component.monitor.dto.CapturedSqlInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 智能 SQL 数据补全组件
 * <p>
 * 职责：根据当前 SQL 上下文，智能获取指定字段的值（优先内存 Params，缺失查 DB）
 * 返回：直接返回获取到的键值对，不修改入参对象
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SmartSqlEnricher {

    private final SqlSessionFactory sqlSessionFactory;

    /**
     * 智能获取字段值
     *
     * @param info             SQL 捕获信息上下文
     * @param targetColumnsStr 需要获取的字段，逗号分隔 (e.g. "merchant_code, city_id")
     * @return 包含目标字段值的 Map (可能不全，取决于是否查到)
     */
    public Map<String, Object> enrich(CapturedSqlInfo info, String targetColumnsStr) {
        Map<String, Object> resultMap = new HashMap<>();
        if (targetColumnsStr == null || info.getParams() == null) {
            return resultMap;
        }

        String[] targets = targetColumnsStr.split(",");
        List<String> missingColumns = new ArrayList<>();
        Map<String, Object> currentParams = info.getParams();

        // --- 1. 内存命中检查 ---
        for (String target : targets) {
            String colName = target.trim();
            // 优先查 Params
            Object val = currentParams.get(colName);
            // 容错查 CamelCase
            if (val == null) {
                val = currentParams.get(BeanColumnUtil.toCamelCase(colName));
            }

            if (val != null) {
                resultMap.put(colName, val);
            } else {
                missingColumns.add(colName);
            }
        }

        // --- 2. 判断是否需要查询 DB ---
        if (missingColumns.isEmpty()) {
            return resultMap; // 全部在内存中命中
        }

        // --- 3. 构建补全 SQL 并查询 ---
        String columnsToQuery = String.join(",", missingColumns);
        Map<String, Object> dbResult = enrichFromDb(info, columnsToQuery);

        // 合并 DB 结果
        if (dbResult != null) {
            resultMap.putAll(dbResult);
        }

        return resultMap;
    }

    private Map<String, Object> enrichFromDb(CapturedSqlInfo info, String columnsToQuery) {
        String executableSql = info.getExecutableSql();
        if (executableSql == null) return null;

        String selectSql = ExecutorSqlHelper.convertUpdateToSelect(executableSql, columnsToQuery);
        if (selectSql == null) {
            log.warn("无法构建补全查询SQL. Raw: {}", info.getRawSql());
            return null;
        }

        long start = System.currentTimeMillis();
        Map<String, Object> dbResult = new HashMap<>();

        try (SqlSession session = sqlSessionFactory.openSession()) {
            Connection conn = session.getConnection();
            try (PreparedStatement ps = conn.prepareStatement(selectSql);
                 ResultSet rs = ps.executeQuery()) {

                if (rs.next()) {
                    for (String col : columnsToQuery.split(",")) {
                        String cleanCol = col.trim();
                        Object dbVal = null;
                        try {
                            dbVal = rs.getObject(cleanCol);
                        } catch (Exception ignored) {
                        }

                        if (dbVal != null) {
                            dbResult.put(cleanCol, dbVal);
                        }
                    }
                    log.debug("【SmartEnrich】DB补全成功, 耗时:{}ms", (System.currentTimeMillis() - start));
                }
            }
        } catch (Exception e) {
            log.error("【SmartEnrich】DB补全查询失败, SQL: {}", selectSql, e);
        }
        return dbResult;
    }
}