package com.dexian.extractor.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class RevenueMetric {
    private String metric;
    private double value;
    private double confidenceScore;
}
