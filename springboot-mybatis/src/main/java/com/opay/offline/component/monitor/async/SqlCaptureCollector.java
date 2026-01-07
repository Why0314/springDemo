package com.opay.offline.component.monitor.async;

import com.opay.offline.component.monitor.dto.CapturedSqlInfo;
import com.opay.offline.component.monitor.dto.SqlCaptureContext;
import com.opay.offline.component.monitor.handler.SqlCaptureHandler;
import com.opay.offline.component.monitor.util.DruidSqlParserHelper;
import com.opay.offline.component.monitor.util.ExecutorSqlHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class SqlCaptureCollector {

    private final List<SqlCaptureHandler> handlers;
    private final SqlCaptureThreadPool threadPool;

    // ✅ 入参改为 Context
    public void submit(SqlCaptureContext context) {
        threadPool.execute(() -> {
            try {
                // 1. 【重活】利用 Context 中的 MyBatis 对象，进行 SQL 解析和组装
                processContext(context);

                // 2. 【脱壳】从 Context 中取出纯净的 DTO
                CapturedSqlInfo info = context.getInfo();

                // 3. 【分发】链式/顺序调用 Handlers
                for (SqlCaptureHandler handler : handlers) {
                    try {
                        handler.onCapture(info);
                    } catch (Exception e) {
                        log.error("Handler [{}] execution failed", handler.getClass().getSimpleName(), e);
                        // 商业级：通常不中断链条，继续下一个，或者根据需求 break
                    }
                }
            } catch (Exception e) {
                log.error("Async capture processing failed", e);
            }
            // 方法结束，Context 局部变量出栈，MyBatis 重对象引用断开，等待 GC
        });
    }

    /**
     * 解析逻辑：使用 Context 填充 Info 的字段
     */
    private void processContext(SqlCaptureContext ctx) {
        CapturedSqlInfo info = ctx.getInfo();

        // 1. 构建 ExecutableSql (需要 Configuration 等)
        if (ctx.getBoundSql() != null && ctx.getConfiguration() != null) {
            String executableSql = ExecutorSqlHelper.buildExecutableSql(
                    ctx.getConfiguration(),
                    ctx.getBoundSql(),
                    ctx.getParameterObject()
            );
            info.setExecutableSql(executableSql);
        }
        reparseParams(info);
    }

    /**
     * 重新解析参数，填充 params 和 whereParams
     */
    private void reparseParams(CapturedSqlInfo info) {
        // 优先使用已填充了参数值的可执行 SQL
        String sql = info.getExecutableSql();
        // 降级：如果没有可执行 SQL，使用原始 SQL (虽然带 ? 但能分析结构)
        if (sql == null || sql.trim().isEmpty()) {
            sql = info.getRawSql();
        }

        if (sql != null && !sql.trim().isEmpty()) {
            try {
                // 调用 Druid 工具类进行 AST 分析
                DruidSqlParserHelper.SqlParamAnalysis analysis = DruidSqlParserHelper.analyzeSqlParams(sql);

                // 填充全量参数 (SET + WHERE)
                // 例如 UPDATE: {province_id=1, id=1}
                info.setParams(new LinkedHashMap<>(analysis.getAllParams()));

                // 填充条件参数 (仅 WHERE)
                // 例如 UPDATE: {id=1}
                info.setWhereParams(new LinkedHashMap<>(analysis.getWhereParams()));

            } catch (Exception e) {
                log.warn("SQL re-parsing failed for SQL: {}", sql, e);
            }
        }
    }
}