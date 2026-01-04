package com.opay.offline.component.monitor.async;


import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Component
public class SqlCaptureThreadPool {

    private final ExecutorService executor =
            new ThreadPoolExecutor(
                    4,                      // core
                    16,                     // max
                    60, TimeUnit.SECONDS,
                    new LinkedBlockingQueue<>(200),
                    r -> new Thread(r, "sql-capture-thread"),
                    new ThreadPoolExecutor.DiscardPolicy() // 不影响主线程
            );

    public void execute(Runnable runnable) {
        executor.execute(new TraceableExecutor(runnable));
    }
}
