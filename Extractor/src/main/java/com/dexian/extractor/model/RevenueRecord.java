package com.dexian.extractor.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

//@Entity
//@Table(name = "revenue_data")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class RevenueRecord {
//    @Id
//    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String cik;
    private String company;
    private String metric;          // e.g. OilRevenue, TotalRevenue
    private LocalDate periodEnd;
    private Double value;
    private String unit;
    private String filingType;
    private LocalDate filingDate;
    private LocalDateTime retrievedAt = LocalDateTime.now();
}
