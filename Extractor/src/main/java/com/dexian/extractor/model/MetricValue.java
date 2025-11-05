package com.dexian.extractor.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "metric_value", schema = "cait_dev")
public class MetricValue {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "metric_value_id")
    private Long metricValueId;

    // 🔗 Relationships
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "metric_id", nullable = false)
    private MetricDefinition metricDefinition;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_document_id", nullable = false)
    private SourceDocument sourceDocument;

    // 📊 Metric Data Fields
    @Column(name = "extracted_metric_value")
    private Double extractedMetricValue;

    @Column(name = "extracted_metric_unit")
    private String extractedMetricUnit;

    @Column(name = "period_start_date")
    private LocalDate periodStartDate;

    @Column(name = "period_end_date")
    private LocalDate periodEndDate;

    @Column(name = "segment_name")
    private String segmentName;

    @Column(name = "basin_name")
    private String basinName;

    @Column(name = "extraction_method")
    private String extractionMethod;

    @Column(name = "extraction_confidence_score")
    private Double extractionConfidenceScore;

    @Column(name = "metric_value")
    private Double metricValue;

    @Column(name = "unit")
    private String unit;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

}
