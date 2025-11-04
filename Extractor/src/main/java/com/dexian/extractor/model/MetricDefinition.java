package com.dexian.extractor.model;


import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "metric_definition", schema = "cait_dev")
public class MetricDefinition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "metric_id")
    private Integer metricId;

    @Column(name = "metric_category", nullable = false)
    private String metricCategory;

    @Column(name = "metric_name_display", nullable = false, unique = true)
    private String metricNameDisplay;

    @Column(name = "metric_name_internal", nullable = false, unique = true)
    private String metricNameInternal;

    @Column(name = "metric_unit")
    private String metricUnit;

    @Column(name = "metric_group")
    private String metricGroup;

    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

}
