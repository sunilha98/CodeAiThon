package com.dexian.extractor.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "source_document", schema = "cait_dev")
public class SourceDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "source_document_id")
    private Integer sourceDocumentId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Column(name = "source_type")
    private String sourceType;

    @Column(name = "filing_type")
    private String filingType;

    @Column(name = "filing_date", nullable = false)
    private LocalDate filingDate;

    @Column(name = "period_end_date")
    private LocalDate periodEndDate;

    @Column(name = "source_url", columnDefinition = "TEXT")
    private String sourceUrl;

    @Column(name = "raw_text_blob_path", columnDefinition = "TEXT")
    private String rawTextBlobPath;

    @Column(name = "file_format")
    private String fileFormat;

    @Column(name = "extraction_confidence_score", precision = 3, scale = 2)
    private BigDecimal extractionConfidenceScore;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();


}
