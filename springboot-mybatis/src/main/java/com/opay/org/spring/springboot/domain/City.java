package com.opay.org.spring.springboot.domain;

import com.opay.offline.component.monitor.annotation.EnableMybatisInterceptorEntity;
import lombok.Data;
import lombok.experimental.Accessors;
import org.apache.ibatis.mapping.SqlCommandType;

/**
 * 城市实体类
 *
 * Created by bysocket on 07/02/2017.
 */
@Data
@Accessors(chain = true)
@EnableMybatisInterceptorEntity(sqlCommandType = {SqlCommandType.UPDATE, SqlCommandType.INSERT, SqlCommandType.SELECT, SqlCommandType.DELETE})
public class City {

    /**
     * 城市编号
     */
    private Long id;

    /**
     * 省份编号
     */
    private Long provinceId;

    /**
     * 城市名称
     */
    private String cityName;

    /**
     * 描述
     */
    private String description;
}
