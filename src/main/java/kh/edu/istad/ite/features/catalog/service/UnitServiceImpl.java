package kh.edu.istad.ite.features.catalog.service;

import kh.edu.istad.ite.features.catalog.dto.UnitResponse;
import kh.edu.istad.ite.features.catalog.mapper.UnitMapper;
import kh.edu.istad.ite.features.catalog.repository.UnitRepository;
import kh.edu.istad.ite.shared.helper.BusinessHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

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
