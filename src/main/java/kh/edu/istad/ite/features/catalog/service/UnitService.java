package kh.edu.istad.ite.features.catalog.service;

import kh.edu.istad.ite.features.catalog.dto.UnitResponse;

import java.util.List;

public interface UnitService {

    List<UnitResponse> findAllUnits();
}
