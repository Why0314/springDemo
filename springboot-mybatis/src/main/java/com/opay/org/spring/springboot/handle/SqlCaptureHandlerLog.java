package com.opay.org.spring.springboot.handle;

import com.alibaba.fastjson.JSON;
import com.opay.offline.component.monitor.dto.CapturedSqlInfo;
import com.opay.offline.component.monitor.handler.SqlCaptureHandler;
import com.opay.offline.component.monitor.util.SmartSqlEnricher;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.util.*;

@Component
@Log4j2
@RequiredArgsConstructor
public class SqlCaptureHandlerLog implements SqlCaptureHandler {

    private final SmartSqlEnricher smartSqlEnricher;

    @Override
    public void onCapture(CapturedSqlInfo info) {
        log.info("SqlCaptureHandlerKyb info:{}", JSON.toJSONString(info));
        Map<String, Object> enrich = smartSqlEnricher.enrich(info, "id,city_name");
        log.info("SqlCaptureHandlerKyb info:{}", JSON.toJSONString(enrich));
    }

}
