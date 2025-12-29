package com.opay.org.spring.springboot.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.opay.org.spring.springboot.entity.City;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 城市业务逻辑接口类
 *
 * Created by bysocket on 07/02/2017.
 */
public interface CityService extends IService<City> {

    /**
     * 根据城市名称，查询城市信息
     * @param cityName
     */
    City findCityByName(String cityName);


    List<City> findListByName( String cityName);

    int addCity(City city);

    int addCityBatch(List<City> cityList);

    int updateOneById(@Param("city")City city);

    int updateByName(@Param("city")City city);

    int deleteByName(@Param("cityName")String cityName);

    int deleteById(@Param("id")Integer id);
}
