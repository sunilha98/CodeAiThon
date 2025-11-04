package com.dexian.extractor.repositories;

import com.dexian.extractor.model.MetricDefinition;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MetricDefinitionRepository extends JpaRepository<MetricDefinition, Integer> {
}
