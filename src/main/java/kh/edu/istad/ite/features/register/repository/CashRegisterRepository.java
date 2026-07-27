package kh.edu.istad.ite.features.register.repository;

import kh.edu.istad.ite.features.register.entity.CashRegister;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CashRegisterRepository extends JpaRepository<CashRegister, Long> {
    List<CashRegister> findByBusinessId(Long businessId);
}
