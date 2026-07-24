package kh.edu.istad.ite.features.catalog.service;

import kh.edu.istad.ite.features.catalog.dto.UnitResponse;
import kh.edu.istad.ite.features.catalog.mapper.UnitMapper;
import kh.edu.istad.ite.features.catalog.repository.UnitRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UnitServiceImpl implements UnitService {

    private final UnitRepository unitRepository;
    private final UnitMapper unitMapper;

    @Override
    @Transactional(readOnly = true)
    public List<UnitResponse> findAllUnits() {
        return unitRepository.findAllByOrderByNameAsc()
                .stream()
                .map(unitMapper::toResponse)
                .toList();
    }
}
