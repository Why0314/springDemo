package com.opay.offline.component.monitor.handler;


import com.alibaba.fastjson.JSON;
import com.opay.offline.component.monitor.dto.CapturedSqlInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class DefaultSqlCaptureHandler implements SqlCaptureHandler {


    @Override
    public void onCapture(CapturedSqlInfo info) {
        log.info("DefaultSqlCaptureHandler info:{}", JSON.toJSONString(info));
    }
}
