package com.opay.org.spring.springboot.handle;

import com.alibaba.fastjson.JSON;
import com.opay.offline.component.monitor.model.CapturedSqlInfo;
import com.opay.offline.component.monitor.handler.SqlCaptureHandler;
import com.opay.offline.component.monitor.support.SqlContextEnricher;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
@Log4j2
@RequiredArgsConstructor
public class SqlCaptureHandlerLog implements SqlCaptureHandler {

    private final SqlContextEnricher sqlContextEnricher;

    @Override
    public void onCapture(CapturedSqlInfo info) {
        log.info("SqlCaptureHandlerKyb info:{}", JSON.toJSONString(info));
        Map<String, Object> enrich = sqlContextEnricher.enrich(info, "id,city_name");
        log.info("SqlCaptureHandlerKyb info:{}", JSON.toJSONString(enrich));
    }

}
