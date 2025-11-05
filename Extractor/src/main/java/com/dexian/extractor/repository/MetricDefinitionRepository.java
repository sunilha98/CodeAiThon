package com.dexian.extractor.repository;

import com.dexian.extractor.model.MetricDefinition;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MetricDefinitionRepository extends JpaRepository<MetricDefinition, Integer> {

    Optional<MetricDefinition> findByMetricNameDisplay(String metricNameDisplay);
}
