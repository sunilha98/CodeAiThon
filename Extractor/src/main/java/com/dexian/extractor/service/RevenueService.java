package com.dexian.extractor.service;

import com.dexian.extractor.clients.SecClient;
import com.dexian.extractor.model.RevenueRecord;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class RevenueService {
    private final SecClient secClient;
//    private final RevenueRepository repo;

    private static final Map<String, String> REVENUE_CONCEPTS = Map.of(
            "OilRevenue", "OilAndGasRevenue",
            "NGLRevenue", "NaturalGasLiquidsRevenue",
            "GasRevenue", "NaturalGasRevenue",
            "TotalRevenue", "Revenues" // US-GAAP concept for total revenue
    );

    public List<RevenueRecord> extractRevenueForCompany(String cik, String company) {
        System.out.printf("[INFO] Starting XBRL revenue extraction for %s (CIK: %s)%n", company, cik);
        List<RevenueRecord> allRecords = new java.util.ArrayList<>();
        try {
            // Step 1: Iterate over the desired revenue metrics (concepts)
            for (Map.Entry<String, String> entry : REVENUE_CONCEPTS.entrySet()) {
                String metricName = entry.getKey();      // e.g., "OilRevenue"
                String xbrlConcept = entry.getValue();   // e.g., "OilAndGasRevenue"

                // Step 2: Fetch structured XBRL data from the SEC API for this concept
                JsonNode conceptJson = secClient.fetchConceptFacts(cik, xbrlConcept);

                if (conceptJson != null) {
                    // Step 3: Use the existing logic to parse and persist the XBRL facts
                    // This method correctly extracts all periods and filing details
                    List<RevenueRecord> revenueRecords = parseFactsAndSave(conceptJson, cik, company, metricName);
                    allRecords.addAll(revenueRecords);
                } else {
                    System.err.printf("[WARN] No XBRL facts found for concept: %s for %s (%s)%n", xbrlConcept, company, cik);
                }
            }

            System.out.printf("[OK] XBRL Revenue extraction completed for %s (%s)%n", company, cik);

        } catch (Exception e) {
            System.err.printf("[ERROR] Failed to extract revenue for %s (%s): %s%n",
                    company, cik, e.getMessage());
            e.printStackTrace();
        }
        return allRecords;
    }

    /**
     * This method is now the primary data processing engine, handling structured XBRL-JSON.
     */
    private List<RevenueRecord> parseFactsAndSave(JsonNode json, String cik, String company, String metric) {
        List<RevenueRecord> results = new java.util.ArrayList<>();
        JsonNode unitsNode = json.path("units");

        // 1. Calculate the cutoff date (one year ago)
        final LocalDate oneYearAgo = LocalDate.now().minusYears(1);
        System.out.printf("[INFO] Filtering records: Only including facts with Period End on or after %s%n", oneYearAgo);

//        if (unitsNode.isMissingNode()) return;

        unitsNode.fields().forEachRemaining(unitEntry -> {
            String unit = unitEntry.getKey();
            for (JsonNode fact : unitEntry.getValue()) {
                LocalDate end = LocalDate.parse(fact.path("end").asText("1900-01-01"));

                // 2. Add the filter condition
                if (end.isBefore(oneYearAgo)) {
                    // Skip the record if its period end date is more than one year ago
                    continue;
                }

                double value = fact.path("val").asDouble();
                String form = fact.path("form").asText("");
                LocalDate filed = fact.has("filed") ? LocalDate.parse(fact.get("filed").asText("1900-01-01")) : null;

                RevenueRecord record = new RevenueRecord();
                record.setCik(cik);
                record.setCompany(company);
                record.setMetric(metric);
                record.setPeriodEnd(end);
                record.setValue(value);
                record.setUnit(unit);
                record.setFilingType(form);
                record.setFilingDate(filed);
                record.setRetrievedAt(LocalDateTime.now()); // Added back for completeness

                System.err.println("[INFO] Extracted " + metric + " for " + company +
                        " | Period End: " + end +
                        " | Value: " + value +
                        " | Unit: " + unit +
                        " | Filing Type: " + form +
                        " | Filing Date: " + filed);
//                repo.save(record);
                results.add(record);
            }
        });
        return results;
    }

    // The following methods, which relied on unreliable HTML scraping, are removed:
    // private Map<String, Double> parseRevenueFromHtml(String htmlContent)
    // private Double extractNumeric(String text)
    // private Double extractAfterLabel(String text, String labelRegex)
    // private Double extractNumericValue(String text)

//    public List<RevenueRecord> getRevenues(String cik) {
//        return repo.findByCikOrderByPeriodEndDesc(cik);
//    }
}