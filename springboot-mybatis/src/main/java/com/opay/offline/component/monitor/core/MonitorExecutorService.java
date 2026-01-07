package com.opay.offline.component.monitor.core;

import com.opay.offline.component.monitor.config.SqlCaptureProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class MonitorExecutorService implements DisposableBean {

    private final ExecutorService executor;

    public MonitorExecutorService(SqlCaptureProperties properties) {
        this.executor = new ThreadPoolExecutor(
                properties.getCorePoolSize(),
                properties.getMaxPoolSize(),
                properties.getKeepAliveSeconds(), TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(properties.getQueueCapacity()),
                r -> new Thread(r, "sql-capture-thread"),
                new ThreadPoolExecutor.DiscardPolicy() // 队列满直接丢弃，绝不阻塞业务主线程
        );
    }

    public void execute(Runnable runnable) {
        executor.execute(new TraceableExecutor(runnable));
    }

    /**
     * 优雅停机：Spring 容器销毁时回调
     */
    @Override
    public void destroy() {
        log.info("正在关闭 SQL 监控线程池...");
        executor.shutdown();
        try {
            // 等待积压任务处理，最多等待 5 秒
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        log.info("SQL 监控线程池已关闭");
    }

}