package com.opay.offline.component.monitor.async;

import com.opay.offline.component.monitor.dto.CapturedSqlInfo;
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

    public void submit(CapturedSqlInfo info) {
        threadPool.execute(() -> {
            try {
                // 1. 先构建可执行 SQL (这一步还是需要的，作为解析的基础)
                // 注意：这里依然需要 info.getBoundSql() 等原始信息来拼接 SQL
                if (info.getBoundSql() != null && info.getConfiguration() != null) {
                    String executableSql = ExecutorSqlHelper.buildExecutableSql(
                            info.getConfiguration(),
                            info.getBoundSql(),
                            info.getParameterObject()
                    );
                    info.setExecutableSql(executableSql);
                }

                // 2. ✅ 核心变更：直接解析 ExecutableSql 提取所有参数
                // 不再需要调用 ExecutorSqlHelper.setParams
                parseParamsFromSql(info);

                // 3. 分发处理
                for (SqlCaptureHandler handler : handlers) {
                    try {
                        handler.onCapture(info);
                    } catch (Exception ignored) {}
                }

            } catch (Exception e) {
                log.error("Monitor async process error", e);
            } finally {
                // 清理大对象引用
                info.setConfiguration(null);
                info.setMappedStatement(null);
                info.setParameterObject(null);
                info.setBoundSql(null);
            }
        });
    }

    private void parseParamsFromSql(CapturedSqlInfo info) {
        String sql = info.getExecutableSql();
        if (sql == null || sql.isEmpty()) {
            sql = info.getRawSql(); // 降级
        }

        // 使用 Druid 解析出所有的 KV 对
        Map<String, Object> parsedMap = DruidSqlParserHelper.extractAllParams(sql);

        // 将解析结果同时赋值给 params (作为全量参数) 和 whereParams (业务兼容)
        // 现在的 params 也是 key-value 形式了，非常清晰
        info.setParams(new LinkedHashMap<>(parsedMap));

        // 如果你想区分 whereParams (仅包含 WHERE 条件)，
        // 可以在 Parser 里拆分 visitor，但通常全量参数对排查问题更有用。
        // 这里简单起见，两个字段存一样的内容，或者你可以让 whereParams 只存部分
        info.setWhereParams(new LinkedHashMap<>(parsedMap));
    }
}