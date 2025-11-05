package com.dexian.extractor.service;

import com.dexian.extractor.model.Company;
import com.dexian.extractor.model.MetricDefinition;
import com.dexian.extractor.model.MetricValue;
import com.dexian.extractor.model.SourceDocument;
import com.dexian.extractor.repository.CompanyRepository;
import com.dexian.extractor.repository.MetricDefinitionRepository;
import com.dexian.extractor.repository.MetricValueRepository;
import com.dexian.extractor.repository.SourceDocumentRepository;
import com.dexian.extractor.util.HtmlParser;
import lombok.RequiredArgsConstructor;
import org.json.JSONObject;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SecExtractorService {

    private final CompanyRepository companyRepository;
    private final SourceDocumentRepository sourceDocumentRepository;
    private final MetricDefinitionRepository metricDefinitionRepository;
    private final MetricValueRepository metricValueRepository;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Transactional
    public String extractAndStoreData(String cik) {
        // 1️⃣ Find or create company
        Company company = companyRepository.findBySecCikNumber(cik)
                .orElseGet(() -> {
                    Company newCompany = new Company();
                    newCompany.setCompanyName("Unknown Company");
                    newCompany.setTickerSymbol("UNK" + cik.substring(Math.max(0, cik.length() - 3)));
                    newCompany.setSecCikNumber(cik);
                    newCompany.setCompanyType("upstream");
                    newCompany.setStatus(true);
                    newCompany.setCreatedAt(LocalDateTime.now());
                    newCompany.setUpdatedAt(LocalDateTime.now());
                    return companyRepository.save(newCompany);
                });

        System.out.println("✅ Using company: " + company.getCompanyName());

        // 2️⃣ Try fetching SEC JSON data
        Map<String, Map<String, Object>> secData = fetchSecJsonData(cik);

        // 3️⃣ If SEC JSON fails, fallback to HTML parsing
        if (secData.isEmpty()) {
            System.out.println("⚠️ SEC JSON not found. Falling back to HTML parser...");
            secData = HtmlParser.extractSecData(cik);
        }

        // 4️⃣ Persist extracted metrics
        for (String quarter : secData.keySet()) {
            Map<String, Object> data = secData.get(quarter);
            saveMetricValues(company, data, quarter);
        }

        return "✅ Extraction and DB load completed for CIK: " + cik;
    }

    /**
     * Fetch data from SEC JSON endpoint, fallback if not found.
     */
    private Map<String, Map<String, Object>> fetchSecJsonData(String cik) {
        Map<String, Map<String, Object>> quarterlyData = new HashMap<>();

        try {
            String apiUrl = String.format("https://data.sec.gov/api/xbrl/company_facts/%010d.json", Long.parseLong(cik));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl))
                    .header("User-Agent", "DexianDataExtractor/1.0 (support@dexian.com)")
                    .header("Accept", "application/json")
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 404) {
                System.err.println("❌ No JSON data found for CIK " + cik);
                return quarterlyData;
            }

            if (response.statusCode() != 200) {
                System.err.println("❌ HTTP error " + response.statusCode() + " for " + apiUrl);
                return quarterlyData;
            }

            JSONObject root = new JSONObject(response.body());
            JSONObject facts = root.optJSONObject("facts");
            if (facts == null) return quarterlyData;

            JSONObject usGaap = facts.optJSONObject("us-gaap");
            if (usGaap == null) return quarterlyData;

            // Extract key metrics
            addMetricData(usGaap, "OilProduction", quarterlyData, "Oil Production");
            addMetricData(usGaap, "NaturalGasProduction", quarterlyData, "Natural Gas Production");
            addMetricData(usGaap, "NaturalGasLiquidProduction", quarterlyData, "Natural Gas Liquid Production");
            addMetricData(usGaap, "TotalProduction", quarterlyData, "Total");

        } catch (Exception e) {
            System.err.println("❌ Error fetching SEC JSON for " + cik + ": " + e.getMessage());
        }

        return quarterlyData;
    }

    private void addMetricData(JSONObject usGaap, String key, Map<String, Map<String, Object>> quarterlyData, String label) {
        if (!usGaap.has(key)) return;

        JSONObject metric = usGaap.getJSONObject(key);
        JSONObject units = metric.optJSONObject("units");
        if (units == null) return;

        for (String unitKey : units.keySet()) {
            var dataArray = units.getJSONArray(unitKey);
            for (int i = 0; i < Math.min(4, dataArray.length()); i++) { // limit to last 4 quarters
                JSONObject entry = dataArray.getJSONObject(i);
                String quarter = "Q" + (i + 1);
                Map<String, Object> quarterData = quarterlyData.computeIfAbsent(quarter, k -> new HashMap<>());
                quarterData.put(label, entry.optDouble("val", 0));
                quarterData.put("source", "https://data.sec.gov/api/xbrl/company_facts");
            }
        }
    }

    /**
     * Save metric values and related source document.
     */
    private void saveMetricValues(Company company, Map<String, Object> data, String quarter) {
        data.forEach((metricName, value) -> {
            if (metricName.equals("source") || value == null) return;

            MetricDefinition metric = metricDefinitionRepository.findByMetricNameDisplay(metricName)
                    .orElseGet(() -> {
                        MetricDefinition newMetric = new MetricDefinition();
                        newMetric.setMetricCategory("OPERATIONAL");
                        newMetric.setMetricNameDisplay(metricName);
                        newMetric.setMetricNameInternal(metricName.toLowerCase().replace(" ", "_"));
                        newMetric.setMetricUnit("BBL");
                        return metricDefinitionRepository.save(newMetric);
                    });

            // Save the source document (10-Q/10-K)
            SourceDocument src = new SourceDocument();
            src.setCompany(company);
            src.setSourceType("SEC_FILING");
            src.setFilingType("10-Q");
            src.setFilingDate(LocalDate.now());
            src.setSourceUrl(data.getOrDefault("source", "").toString());
            src.setFileFormat("HTML");
            sourceDocumentRepository.save(src);

            MetricValue mv = new MetricValue();
            mv.setCompany(company);
            mv.setMetricDefinition(metric);
            mv.setSourceDocument(src);
            mv.setPeriodStartDate(LocalDate.now().minusMonths(3));
            mv.setPeriodEndDate(LocalDate.now());
            mv.setExtractedMetricValue(Double.parseDouble(value.toString()));
            mv.setExtractionMethod("SEC_JSON");
            mv.setExtractionConfidenceScore(1.0);
            mv.setSegmentName("Upstream");
            mv.setCreatedAt(LocalDateTime.now());
            metricValueRepository.save(mv);
        });
    }
}
