package com.opay.offline.component.monitor.core;

import com.opay.offline.component.monitor.config.SqlCaptureProperties;
import com.opay.offline.component.monitor.model.CapturedSqlInfo;
import com.opay.offline.component.monitor.model.SqlCaptureContext;
import com.opay.offline.component.monitor.handler.SqlCaptureHandler;
import com.opay.offline.component.monitor.support.SqlBuilderUtils;
import com.opay.offline.component.monitor.support.DruidSqlParserHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class SqlCaptureDispatcher {

    private final List<SqlCaptureHandler> handlers;
    private final MonitorExecutorService threadPool;
    private final SqlCaptureProperties properties;

    public void submit(SqlCaptureContext context) {
        threadPool.execute(() -> {
            try {
                // 1. 利用 Context 构建可执行 SQL
                processContext(context);

                // 2. 获取纯净 DTO
                CapturedSqlInfo info = context.getInfo();

                // 3. 分发给所有 Handler
                for (SqlCaptureHandler handler : handlers) {
                    try {
                        handler.onCapture(info);
                    } catch (Exception e) {
                        log.error("Handler [{}] failed", handler.getClass().getSimpleName(), e);
                    }
                }
            } catch (Exception e) {
                log.error("Async capture failed", e);
            }
            // Context 在此作用域结束，帮助 GC 回收 MyBatis 重对象
        });
    }

    private void processContext(SqlCaptureContext ctx) {
        CapturedSqlInfo info = ctx.getInfo();

        if (ctx.getBoundSql() != null && ctx.getConfiguration() != null) {
            // 组装 SQL
            String executableSql = SqlBuilderUtils.buildExecutableSql(
                    ctx.getConfiguration(),
                    ctx.getBoundSql(),
                    ctx.getParameterObject()
            );

            // 安全检查：超长 SQL 截断
            if (executableSql != null && executableSql.length() > properties.getMaxSqlLength()) {
                info.setExecutableSql(executableSql.substring(0, properties.getMaxSqlLength()) + " ...[TRUNCATED]");
                // 超长 SQL 放弃解析参数，防止 Druid 卡死
                return;
            }
            info.setExecutableSql(executableSql);
        }

        // 核心：基于 Druid AST 解析参数 (区分 SET 和 WHERE)
        reparseParams(info);
    }

    private void reparseParams(CapturedSqlInfo info) {
        String sql = info.getExecutableSql();
        if (sql == null || sql.trim().isEmpty()) {
            sql = info.getRawSql();
        }

        if (sql != null && !sql.trim().isEmpty()) {
            try {
                // 使用 ExecutorSqlHelper 中的 Druid 逻辑
                DruidSqlParserHelper.SqlParamAnalysis analysis = DruidSqlParserHelper.analyzeSqlParams(sql);

                // 设置全量参数 (SET + WHERE)
                info.setParams(new LinkedHashMap<>(analysis.getAllParams()));

                // 设置条件参数 (仅 WHERE)
                info.setWhereParams(new LinkedHashMap<>(analysis.getWhereParams()));
            } catch (Exception e) {
                log.warn("Param parsing failed for SQL", e);
            }
        }
    }
}