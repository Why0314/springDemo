package com.opay.offline.component.monitor.support;

import com.opay.offline.component.monitor.model.CapturedSqlInfo;
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

@Slf4j
@Component
@RequiredArgsConstructor
public class SqlContextEnricher {

    private final SqlSessionFactory sqlSessionFactory;

    /**
     * 智能获取字段值 (优先内存，缺失查库)
     * 返回 Map，不修改 info 对象，无副作用
     */
    public Map<String, Object> enrich(CapturedSqlInfo info, String targetColumnsStr) {
        Map<String, Object> resultMap = new HashMap<>();
        if (targetColumnsStr == null || info.getParams() == null) {
            return resultMap;
        }

        String[] targets = targetColumnsStr.split(",");
        List<String> missingColumns = new ArrayList<>();
        Map<String, Object> currentParams = info.getParams();

        // 1. 内存查找
        for (String target : targets) {
            String colName = target.trim();
            Object val = currentParams.get(colName);
            // 容错: 尝试 CamelCase
            if (val == null) {
                val = currentParams.get(BeanColumnUtil.toCamelCase(colName));
            }

            if (val != null) {
                resultMap.put(colName, val);
            } else {
                missingColumns.add(colName);
            }
        }

        // 2. 如果全命中，直接返回
        if (missingColumns.isEmpty()) {
            return resultMap;
        }

        // 3. DB 补全查询
        String columnsToQuery = String.join(",", missingColumns);
        Map<String, Object> dbResult = enrichFromDb(info, columnsToQuery);
        if (dbResult != null) {
            resultMap.putAll(dbResult);
        }

        return resultMap;
    }

    private Map<String, Object> enrichFromDb(CapturedSqlInfo info, String columnsToQuery) {
        String executableSql = info.getExecutableSql();
        if (executableSql == null) return null;

        // 使用工具类转换 UPDATE -> SELECT
        String selectSql = SqlBuilderUtils.convertUpdateToSelect(executableSql, columnsToQuery);
        if (selectSql == null) return null;

        long start = System.currentTimeMillis();
        Map<String, Object> dbResult = new HashMap<>();

        try (SqlSession session = sqlSessionFactory.openSession()) {
            Connection conn = session.getConnection();
            try (PreparedStatement ps = conn.prepareStatement(selectSql);
                 ResultSet rs = ps.executeQuery()) {

                if (rs.next()) {
                    for (String col : columnsToQuery.split(",")) {
                        String cleanCol = col.trim();
                        try {
                            Object val = rs.getObject(cleanCol);
                            if (val != null) dbResult.put(cleanCol, val);
                        } catch (Exception ignored) {}
                    }
                }
            }
            log.debug("DB补全耗时: {}ms", System.currentTimeMillis() - start);
        } catch (Exception e) {
            log.error("DB补全查询失败: {}", selectSql, e);
        }
        return dbResult;
    }
}