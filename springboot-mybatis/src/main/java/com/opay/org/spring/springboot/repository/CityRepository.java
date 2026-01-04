package com.opay.org.spring.springboot.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import com.opay.org.spring.springboot.entity.City;

import java.util.List;

/**
 * 城市 DAO 接口类
 *
 * Created by bysocket on 07/02/2017.
 */
public interface CityRepository extends BaseMapper<City> {

    /**
     * 根据城市名称，查询城市信息
     *
     * @param cityName 城市名
     */
    City findByName(@Param("cityName") String cityName);

    List<City> findListByName(@Param("cityName") String cityName);

    int addCity(@Param("city")City city);

    int addCityBatch(@Param("list") List<City> cityList);

    int updateById(@Param("city")City city);

    int updateByName(@Param("city")City city);

    int deleteByName(@Param("cityName")String cityName);

    int deleteById(@Param("id")Integer id);
}
