package com.opay.org.spring.springboot.controller;

import com.opay.org.spring.springboot.entity.City;
import com.opay.org.spring.springboot.service.CityService;
import org.apache.ibatis.annotations.Param;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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
    int addCity(@RequestBody City city){
        return cityService.addCity(city);
    }
    @RequestMapping(value = "/api/addCityBatch", method = RequestMethod.POST)
    int addCityBatch(@RequestBody List<City> cityList){
        return cityService.addCityBatch(cityList);
    }
    @RequestMapping(value = "/api/updateOneById", method = RequestMethod.POST)
    int updateOneById(@RequestBody City city){
        return cityService.updateOneById(city);
    }
    @RequestMapping(value = "/api/updateByName", method = RequestMethod.POST)
    int updateByName(@RequestBody City city){
        return cityService.updateByName(city);
    }
    @RequestMapping(value = "/api/deleteByName", method = RequestMethod.GET)
    int deleteByName(@Param("cityName") String cityName){
        return cityService.deleteByName(cityName);
    }
    @RequestMapping(value = "/api/deleteById", method = RequestMethod.GET)
    int deleteById(@Param("id") Integer id){
        return cityService.deleteById(id);
    }
}
