package com.opay.offline.component.monitor.async;


import com.opay.offline.component.monitor.dto.CapturedSqlInfo;
import com.opay.offline.component.monitor.handler.SqlCaptureHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class SqlCaptureCollector {

    private final List<SqlCaptureHandler> handlers;   // 自动注入多个实现

    private final SqlCaptureThreadPool threadPool;

    public void submit(CapturedSqlInfo info) {
        // 不影响主线程
        threadPool.execute(() -> {
            for (SqlCaptureHandler handler : handlers) {
                try {
                    handler.onCapture(info);
                } catch (Exception ignored) {
                }
            }
        });
    }
}
