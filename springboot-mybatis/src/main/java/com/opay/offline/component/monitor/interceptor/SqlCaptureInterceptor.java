package com.opay.offline.component.monitor.interceptor;

import com.opay.offline.component.monitor.annotation.MonitorSql;
import com.opay.offline.component.monitor.core.SqlCaptureDispatcher;
import com.opay.offline.component.monitor.model.CapturedSqlInfo;
import com.opay.offline.component.monitor.model.SqlCaptureContext;
import com.opay.offline.component.monitor.support.SqlBuilderUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.*;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Properties;

@Slf4j
@Component
@Intercepts({
        @Signature(type = Executor.class, method = "update", args = {MappedStatement.class, Object.class}),
        @Signature(type = Executor.class, method = "query", args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class})
})
public class SqlCaptureInterceptor implements Interceptor {

    // ✅ 核心：使用 @Lazy 打破循环依赖 (Interceptor -> Collector -> Handler -> Enricher -> SqlSessionFactory -> Interceptor)
    @Autowired
    @Lazy
    private SqlCaptureDispatcher sqlCaptureDispatcher;

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        Object[] args = invocation.getArgs();
        MappedStatement ms = (MappedStatement) args[0];

        // 1. 快速判断是否需要拦截 (基于缓存)
        Class<?> entityClass = SqlBuilderUtils.getEntityClass(ms.getId());
        if (entityClass == null || !entityClass.isAnnotationPresent(MonitorSql.class)) {
            return invocation.proceed();
        }

        // 2. 准备上下文
        CapturedSqlInfo info = new CapturedSqlInfo();
        info.setEntityClassName(entityClass.getSimpleName());
        info.setMapperMethod(ms.getId());
        info.setSqlCommandType(ms.getSqlCommandType());

        // 使用 Context 包装 MyBatis 重对象，避免在 DTO 中长期持有
        SqlCaptureContext context = new SqlCaptureContext(info);
        context.setConfiguration(ms.getConfiguration());
        context.setMappedStatement(ms);
        context.setParameterObject(args.length > 1 ? args[1] : null);

        try {
            // 尝试获取 BoundSql (轻微计算)
            context.setBoundSql(ms.getBoundSql(context.getParameterObject()));
            info.setRawSql(context.getBoundSql().getSql());
        } catch (Exception ignored) {
            // 忽略异常，保证主流程不中断
        }

        boolean success = true;
        Object result = null;
        long start = System.currentTimeMillis();

        try {
            // 3. 执行原业务逻辑
            result = invocation.proceed();

            // 4. 处理结果 (截断大列表)
            handleResultPolicy(info, result);

            return result;
        } catch (Throwable t) {
            success = false;
            info.setResult("Exception: " + t.getMessage());
            throw t;
        } finally {
            info.setDurationMillis(System.currentTimeMillis() - start);
            info.setSuccess(success);

            // 5. 异步提交
            sqlCaptureDispatcher.submit(context);
        }
    }

    private void handleResultPolicy(CapturedSqlInfo info, Object result) {
        if (result == null) {
            info.setResult(null);
            return;
        }
        if (result instanceof Collection) {
            Collection<?> col = (Collection<?>) result;
            int size = col.size();
            // 策略：超过 10 条不记录详细内容，防止内存溢出
            if (size > 10) {
                // 只记录第一条作为参考，或者干脆 null
                Object first = col.isEmpty() ? null : col.iterator().next();
                info.setResult(first != null ? "First: " + first + " ... (Total: " + size + ")" : "Size: " + size);
                info.setResultSummary("List Size: " + size + " (Truncated)");
            } else {
                info.setResult(result);
                info.setResultSummary("List Size: " + size);
            }
        } else {
            info.setResult(result);
            info.setResultSummary(String.valueOf(result));
        }
    }

    @Override
    public Object plugin(Object target) {
        return Plugin.wrap(target, this);
    }

    @Override
    public void setProperties(Properties properties) {}
}