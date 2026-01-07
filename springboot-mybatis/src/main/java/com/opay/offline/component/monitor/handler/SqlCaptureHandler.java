package com.opay.offline.component.monitor.handler;


import com.opay.offline.component.monitor.dto.CapturedSqlInfo;

public abstract interface SqlCaptureHandler {

    void onCapture(CapturedSqlInfo info);
}
