package com.opay.offline.component.monitor.interceptor;

import com.opay.offline.component.monitor.async.SqlCaptureCollector;
import com.opay.offline.component.monitor.dto.CapturedSqlInfo;
import com.opay.offline.component.monitor.util.ExecutorSqlHelper;
import com.opay.offline.component.monitor.annotation.EnableMybatisInterceptorEntity;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.*;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Properties;

@Component
@Intercepts({
        @Signature(type = Executor.class, method = "update", args = {MappedStatement.class, Object.class}),
        @Signature(type = Executor.class, method = "query", args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class})
})
@Slf4j
public class MybatisEntityInterceptor implements Interceptor {

    @Autowired
    private SqlCaptureCollector sqlCaptureCollector;

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        // ... (前置获取 entityClass, info 初始化等逻辑保持不变) ...
        // 参考之前的优化代码 ...

        Object[] args = invocation.getArgs();
        MappedStatement ms = (MappedStatement) args[0];
        Class<?> entityClass = ExecutorSqlHelper.getEntityClass(ms.getId());
        if (entityClass == null || !entityClass.isAnnotationPresent(EnableMybatisInterceptorEntity.class)) {
            return invocation.proceed();
        }

        CapturedSqlInfo info = new CapturedSqlInfo();
        // ... 初始化 info 基本信息 ...
        info.setEntityClassName(entityClass.getSimpleName());
        info.setMapperMethod(ms.getId());
        info.setSqlCommandType(ms.getSqlCommandType());
        // 保存上下文引用供异步处理
        info.setConfiguration(ms.getConfiguration());
        info.setMappedStatement(ms);
        info.setParameterObject(args.length > 1 ? args[1] : null);
        try {
            info.setBoundSql(ms.getBoundSql(info.getParameterObject()));
            info.setRawSql(info.getBoundSql().getSql());
        } catch (Exception ignored) {}


        boolean success = true;
        Object result = null;
        long start = System.currentTimeMillis();

        try {
            // 1. 执行 SQL
            result = invocation.proceed();

            // 2. ✅【核心修改】处理结果策略
            handleResultPolicy(info, result);

            return result;
        } catch (Throwable t) {
            success = false;
            // 异常时记录错误信息
            info.setResult("Exception: " + t.getMessage());
            throw t;
        } finally {
            info.setDurationMillis(System.currentTimeMillis() - start);
            info.setSuccess(success);
            // 提交异步
            sqlCaptureCollector.submit(info);
        }
    }

    /**
     * 结果处理策略：
     * 1. 如果是查询结果(List)，<=10条记录，>10条记录null
     * 2. 如果是更新/插入结果(Integer/Boolean)，直接记录
     */
    private void handleResultPolicy(CapturedSqlInfo info, Object result) {
        if (result == null) {
            info.setResult(null);
            return;
        }

        // 判断是否为集合 (MyBatis Executor.query 永远返回 List)
        if (result instanceof Collection) {
            Collection<?> collection = (Collection<?>) result;
            int size = collection.size();

            // ✅ 策略：超过 10 条，不记录 result (设为 null 或提示语)
            if (size > 10) {
                // 方式 A: 严格遵照你的要求，设为 null
                info.setResult(null);

                // 方式 B (建议): 存一个简单的字符串提示，区分"查不到"和"太多了"
                // info.setResult("[Result truncated: size=" + size + " > 10]");

                // 同时也记录一下摘要，方便知道到底查了多少条
                info.setResultSummary("List Size: " + size + " (Truncated)");
            } else {
                // ✅ 策略：<= 10 条，记录完整结果
                // 注意：这里是引用传递。如果后续代码修改了 List 内容，异步线程也会看到修改后的。
                // 通常 Service 层拿到 List 很少修改内容，所以直接传引用在性能上是最高效的。
                info.setResult(result);
                info.setResultSummary("List Size: " + size);
            }
        }
        // 处理 Update/Insert/Delete 返回的 Integer/Long/Boolean
        else {
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