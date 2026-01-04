package com.opay.offline.component.monitor.async;


import org.slf4j.MDC;

import java.util.Map;

public class TraceableExecutor implements Runnable {

    private final Runnable task;
    private final Map<String, String> contextMap;

    public TraceableExecutor(Runnable task) {
        this.task = task;
        this.contextMap = MDC.getCopyOfContextMap();
    }

    @Override
    public void run() {
        try {
            if (contextMap != null) {
                MDC.setContextMap(contextMap);
            }
            task.run();
        } finally {
            MDC.clear();
        }
    }
}
