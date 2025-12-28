package com.opay.org.spring.springboot.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import com.opay.org.spring.springboot.domain.City;

/**
 * 城市 DAO 接口类
 *
 * Created by bysocket on 07/02/2017.
 */
public interface CityDao extends BaseMapper<CityDao> {

    /**
     * 根据城市名称，查询城市信息
     *
     * @param cityName 城市名
     */
    City findByName(@Param("cityName") String cityName);
}
