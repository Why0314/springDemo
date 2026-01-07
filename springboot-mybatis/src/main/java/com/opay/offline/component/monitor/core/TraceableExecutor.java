package com.opay.offline.component.monitor.core;


import org.slf4j.MDC;

import java.util.Map;

/**
 * MDC 透传装饰器，保证异步线程能拿到 TraceId
 */
public class TraceableExecutor implements Runnable {
    private final Runnable task;
    private final Map<String, String> contextMap;

    public TraceableExecutor(Runnable task) {
        this.task = task;
        this.contextMap = MDC.getCopyOfContextMap();
    }

    @Override
    public void run() {
        if (contextMap != null) {
            MDC.setContextMap(contextMap);
        }
        try {
            task.run();
        } finally {
            MDC.clear();
        }
    }
}