package com.opay.offline.component.monitor.interceptor;

import com.alibaba.fastjson.JSONObject;
import com.opay.offline.component.monitor.annotation.EnableMybatisInterceptorEntity;
import com.opay.offline.component.monitor.async.SqlCaptureCollector;
import com.opay.offline.component.monitor.dto.CapturedSqlInfo;
import com.opay.offline.component.monitor.util.ExecutorSqlHelper;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.*;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Properties;

@Component
@Intercepts({
        @Signature(type = Executor.class, method = "update",
                args = {MappedStatement.class, Object.class}),
        @Signature(type = Executor.class, method = "query",
                args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class})
})
@Slf4j
public class MybatisEntityInterceptor implements Interceptor {
    @Autowired
    private SqlCaptureCollector sqlCaptureCollector;

    @Override
    public Object intercept(Invocation invocation) throws Throwable {

        MappedStatement ms = (MappedStatement) invocation.getArgs()[0];
        Object param = invocation.getArgs().length > 1 ? invocation.getArgs()[1] : null;
        // 解析 Mapper 对应的实体类
        Class<?> entityClass = ExecutorSqlHelper.getEntityClass(ms.getId());
        log.info("Mybatis拦截器 开始");
        // -------- 仅拦截被注解的实体类 ----------
        if (param == null) {
            log.info("Mybatis拦截器 param isnull");
            return invocation.proceed();
        }
        EnableMybatisInterceptorEntity cacheEntity = entityClass.getAnnotation(EnableMybatisInterceptorEntity.class);
        if (cacheEntity == null) {
            log.info("Mybatis拦截器 注解不存在");
            return invocation.proceed();
        }

        CapturedSqlInfo info = new CapturedSqlInfo();
        info.setEntityClassName(entityClass.getSimpleName());
        info.setMapperMethod(ms.getId());
        ExecutorSqlHelper.setParams(ms, ms.getBoundSql(param), param, info);
        info.setRawSql(ms.getBoundSql(param).getSql());
        info.setSqlCommandType(ms.getSqlCommandType());
        info.setExecutableSql(ExecutorSqlHelper.buildExecutableSql(
                ms.getConfiguration(), ms, param
        ));
        log.info("Mybatis拦截器 info:{}", JSONObject.toJSONString(info));
        boolean success = true;
        Object result = null;

        try {
            long start = System.currentTimeMillis();
            result = invocation.proceed();
            info.setResult(result);
            info.setDurationMillis(System.currentTimeMillis() - start);
        } catch (Throwable t) {
            log.error("Mybatis拦截器 error:", t);
            success = false;
            throw t;
        } finally {
            info.setSuccess(success);
            sqlCaptureCollector.submit(info);
            log.info("Mybatis拦截器 submit:{}", JSONObject.toJSONString(info));
        }

        return result;
    }

    @Override
    public Object plugin(Object target) {
        return Plugin.wrap(target, this);
    }

    @Override
    public void setProperties(Properties properties) {

    }
}
