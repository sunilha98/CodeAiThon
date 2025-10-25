package com.dexian.extractor.service;

import com.dexian.extractor.clients.SecClient;
import com.dexian.extractor.model.RevenueRecord;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RevenueService {

    private final SecClient secClient;

    /**
     * Extracts all revenue-related us-gaap facts with actual values (not just labels).
     * Filters facts newer than one year.
     */
    public List<RevenueRecord> extractRecentUsGaapRevenueFacts(String cik, String company) {
        System.out.printf("[INFO] Fetching all us-gaap facts for %s (CIK: %s)%n", company, cik);
        List<RevenueRecord> records = new ArrayList<>();

        JsonNode root = secClient.fetchAllCompanyFacts(cik);
        if (root == null) {
            System.err.printf("[ERROR] No data found for %s (%s)%n", company, cik);
            return records;
        }

        JsonNode usGaap = root.path("facts").path("us-gaap");
        if (usGaap.isMissingNode()) {
            System.err.println("[WARN] No 'us-gaap' section found.");
            return records;
        }

        LocalDate oneYearAgo = LocalDate.now().minusYears(1);
        System.out.printf("[INFO] Extracting only 'revenue' tags newer than %s%n", oneYearAgo);

        Iterator<String> concepts = usGaap.fieldNames();
        while (concepts.hasNext()) {
            String concept = concepts.next();
            if (!concept.toLowerCase().contains("revenue") && !concept.toLowerCase().contains("gas")
                    && !concept.toLowerCase().contains("oil") && !concept.toLowerCase().contains("liquid") &&
                    !concept.toLowerCase().contains("natural")) continue;

            JsonNode conceptNode = usGaap.get(concept);
            JsonNode unitsNode = conceptNode.path("units");
            if (unitsNode.isMissingNode()) continue;

            // ✅ Extract all numeric entries
            unitsNode.fields().forEachRemaining(unitEntry -> {
                String unit = unitEntry.getKey();
                for (JsonNode fact : unitEntry.getValue()) {
                    try {
                        if (!fact.has("end")) continue;
                        LocalDate end = LocalDate.parse(fact.get("end").asText("1900-01-01"));
                        if (end.isBefore(oneYearAgo)) continue;

                        double val = fact.path("val").asDouble(Double.NaN);
                        if (Double.isNaN(val)) continue;

                        String form = fact.path("form").asText("");
                        LocalDate filed = fact.has("filed")
                                ? LocalDate.parse(fact.get("filed").asText("1900-01-01"))
                                : null;

                        RevenueRecord record = new RevenueRecord();
                        record.setCik(cik);
                        record.setCompany(company);
                        record.setMetric(concept);
                        record.setValue(val);
                        record.setUnit(unit);
                        record.setPeriodEnd(end);
                        record.setFilingType(form);
                        record.setFilingDate(filed);
                        record.setRetrievedAt(LocalDateTime.now());

                        records.add(record);

                        System.err.println(concept);

                    } catch (Exception e) {
                        // Ignore invalid records
                    }
                }
            });
        }

        System.out.printf("[OK] Extracted %d revenue facts for %s (%s)%n",
                records.size(), company, cik);
        return records;
    }
}
