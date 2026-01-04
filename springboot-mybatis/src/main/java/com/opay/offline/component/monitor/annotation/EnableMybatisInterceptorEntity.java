package com.opay.offline.component.monitor.annotation;

import org.apache.ibatis.mapping.SqlCommandType;

import java.lang.annotation.*;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface EnableMybatisInterceptorEntity {
    /**
     * 拦截的操作类型
     *
     * @return
     */
    SqlCommandType[] sqlCommandType();
}
