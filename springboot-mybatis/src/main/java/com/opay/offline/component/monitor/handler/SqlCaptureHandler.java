package com.opay.offline.component.monitor.handler;


import com.opay.offline.component.monitor.model.CapturedSqlInfo;

public  interface SqlCaptureHandler {

    void onCapture(CapturedSqlInfo info);
}
