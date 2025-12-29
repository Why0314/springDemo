package com.opay.org.spring.springboot.handle;

import com.alibaba.fastjson.JSON;
import com.opay.offline.component.monitor.dto.CapturedSqlInfo;
import com.opay.offline.component.monitor.handler.SqlCaptureHandler;
import lombok.extern.log4j.Log4j2;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.util.*;

@Component
@Log4j2
public class SqlCaptureHandlerLog implements SqlCaptureHandler {
    @Lazy
    @Autowired
    private SqlSessionFactory sqlSessionFactory;

    @Override
    public void onCapture(CapturedSqlInfo info) {
        log.info("SqlCaptureHandlerKyb info:{}", JSON.toJSONString(info));
    }

    // --- 独立SqlSession查询 ---
    private List<Map<String, Object>> queryWithNewSession(String sql, List<Object> params) {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            Connection connection = session.getConnection();
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                // 简单示例：按params顺序设置参数，如果SQL中使用了 ?, 可以按需要改进
                for (int i = 0; i < params.size(); i++) {
                    ps.setObject(i + 1, params.get(i));
                }
                ResultSet rs = ps.executeQuery();
                List<Map<String, Object>> list = new ArrayList<>();
                ResultSetMetaData meta = rs.getMetaData();
                while (rs.next()) {
                    Map<String, Object> row = new HashMap<>();
                    for (int j = 1; j <= meta.getColumnCount(); j++) {
                        row.put(meta.getColumnName(j).toLowerCase(), rs.getObject(j));
                    }
                    list.add(row);
                }
                return list;
            }
        } catch (Throwable e) {
            log.error("执行查询失败: {}", sql, e);
            return Collections.emptyList();
        }
    }
}
