package com.dexian.extractor.repositories;

import com.dexian.extractor.model.MetricValue;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MetricValueRepository extends JpaRepository<MetricValue, Integer> {
}
