package com.opay.org.spring.springboot.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.opay.org.spring.springboot.repository.CityRepository;
import com.opay.org.spring.springboot.entity.City;
import com.opay.org.spring.springboot.service.CityService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.List;

/**
 * 城市业务逻辑实现类
 * <p>
 * Created by bysocket on 07/02/2017.
 */
@Service
public class CityServiceImpl extends ServiceImpl<CityRepository, City> implements CityService {

    @Resource
    private CityRepository cityRepository;

    @Override
    public City findCityByName(String cityName) {
        return cityRepository.findByName(cityName);
    }

    @Override
    public List<City> findListByName(String cityName) {
        return cityRepository.findListByName(cityName);
    }

    @Override
    public int addCity(City city) {
        return cityRepository.addCity(city);
    }

    @Override
    public int addCityBatch(List<City> cityList) {
        return cityRepository.addCityBatch(cityList);
    }

    @Override
    public int updateOneById(City city) {
        return cityRepository.updateDataById(city);
    }

    @Override
    public int updateByName(City city) {
        return cityRepository.updateByName(city);
    }

    @Override
    public int deleteByName(String cityName) {
        return cityRepository.deleteByName(cityName);
    }

    @Override
    public int deleteById(Integer id) {
        return cityRepository.deleteById(id);
    }

}
