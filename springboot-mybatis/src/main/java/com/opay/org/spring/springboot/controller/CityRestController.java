package com.opay.org.spring.springboot.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.opay.org.spring.springboot.entity.City;
import com.opay.org.spring.springboot.service.CityService;
import org.apache.ibatis.annotations.Param;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Objects;

/**
 * Created by bysocket on 07/02/2017.
 */
@RestController
public class CityRestController {

    @Autowired
    private CityService cityService;

    @RequestMapping(value = "/api/findCityByName", method = RequestMethod.GET)
    public City findOneCity(@RequestParam(value = "cityName", required = true) String cityName) {
        return cityService.findCityByName(cityName);
    }

    @RequestMapping(value = "/api/findListByName", method = RequestMethod.GET)
    List<City> findListByName(@RequestParam(value = "cityName", required = true) String cityName) {
        return cityService.findListByName(cityName);
    }

    @RequestMapping(value = "/api/addCity", method = RequestMethod.POST)
    int addCity(@RequestBody City city) {
        return cityService.addCity(city);
    }

    @RequestMapping(value = "/api/addCityBatch", method = RequestMethod.POST)
    int addCityBatch(@RequestBody List<City> cityList) {
        return cityService.addCityBatch(cityList);
    }

    @RequestMapping(value = "/api/updateOneById", method = RequestMethod.POST)
    int updateOneById(@RequestBody City city) {
        return cityService.updateOneById(city);
    }

    @RequestMapping(value = "/api/updateByName", method = RequestMethod.POST)
    int updateByName(@RequestBody City city) {
        return cityService.updateByName(city);
    }

    @RequestMapping(value = "/api/deleteByName", method = RequestMethod.GET)
    int deleteByName(@Param("cityName") String cityName) {
        return cityService.deleteByName(cityName);
    }

    @RequestMapping(value = "/api/deleteById", method = RequestMethod.GET)
    int deleteById(@Param("id") Integer id) {
        return cityService.deleteById(id);
    }


    @RequestMapping(value = "/api2/findCityByName", method = RequestMethod.GET)
    public City findOneCity2(@RequestParam(value = "cityName", required = true) String cityName) {
        LambdaQueryWrapper<City> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.select(City::getCityName, City::getDescription);
        queryWrapper.eq(City::getCityName, cityName);
        return cityService.getOne(queryWrapper);
    }

    @RequestMapping(value = "/api2/findListByName", method = RequestMethod.GET)
    List<City> findListByName2(@RequestParam(value = "cityName", required = true) String cityName) {
        LambdaQueryWrapper<City> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(City::getCityName, cityName);
        return cityService.list(queryWrapper);
    }

    @RequestMapping(value = "/api2/addCity", method = RequestMethod.POST)
    boolean addCity2(@RequestBody City city) {
        return cityService.save(city);
    }

    @RequestMapping(value = "/api2/addCityBatch", method = RequestMethod.POST)
    boolean addCityBatch2(@RequestBody List<City> cityList) {
        //这不对
        return cityService.saveBatch(cityList);
    }

    @RequestMapping(value = "/api2/updateOneById", method = RequestMethod.POST)
    boolean updateOneById2(@RequestBody City city) {
        return cityService.updateById(city);
    }

    @RequestMapping(value = "/api2/updateByName", method = RequestMethod.POST)
    boolean updateByName2(@RequestBody City city) {
        //这不对
        LambdaUpdateWrapper<City> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(City::getCityName, city.getCityName());
        updateWrapper.set(StringUtils.isNotEmpty(city.getDescription()), City::getDescription, city.getDescription());
        updateWrapper.set(Objects.nonNull(city.getProvinceId()), City::getProvinceId, city.getProvinceId());
        return cityService.update(updateWrapper);
    }

    @RequestMapping(value = "/api2/deleteByName", method = RequestMethod.GET)
    boolean deleteByName2(@Param("cityName") String cityName) {
        LambdaUpdateWrapper<City> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(City::getCityName, cityName);
        return cityService.remove(updateWrapper);
    }

    @RequestMapping(value = "/api2/deleteById", method = RequestMethod.GET)
    boolean deleteById2(@Param("id") Integer id) {
        return cityService.removeById(id);
    }

}
