package com.qcomm.engine.repository;

import com.qcomm.engine.model.DarkStore;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DarkStoreRepository extends JpaRepository<DarkStore, Long> {

    List<DarkStore> findByIsActiveTrue();

    List<DarkStore> findByIsActiveTrueAndBeltCodeIgnoreCase(String beltCode);

    List<DarkStore> findByBeltCodeIgnoreCase(String beltCode);
}
