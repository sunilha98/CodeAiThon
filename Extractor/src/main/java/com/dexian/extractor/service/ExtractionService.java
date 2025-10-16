package com.dexian.extractor.service;

import com.dexian.extractor.clients.EDGARClient;
import com.dexian.extractor.model.RevenueMetric;
import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ExtractionService {

    private final EDGARClient edgarClient;

    public List<RevenueMetric> extractRevenueMetrics(String cik) {
        String html = edgarClient.fetchLatest10QHtml(cik);
        List<RevenueMetric> results = new ArrayList<>();

        if (html != null) {
            Document doc = Jsoup.parse(html);
            Elements tables = doc.select("table");

            for (Element table : tables) {
                String tableText = table.text();
                if (tableText.toLowerCase().contains("revenue")) {
                    if (tableText.toLowerCase().contains("total revenue")) {
                        // Very simple regex-like logic
                        String[] tokens = tableText.split("\\s+");
                        for (int i = 0; i < tokens.length; i++) {
                            if (tokens[i].equalsIgnoreCase("Revenue") && i + 1 < tokens.length) {
                                try {
                                    String rawValue = tokens[i + 1].replaceAll("[^0-9.]", "");
                                    double value = Double.parseDouble(rawValue);

                                    RevenueMetric metric = new RevenueMetric();
                                    metric.setMetric("Total Revenue");
                                    metric.setValue(value);
                                    metric.setConfidenceScore(0.75);
                                    results.add(metric);
                                    break;
                                } catch (NumberFormatException ignored) {
                                }
                            }
                        }
                    }
                }
            }
        }

        return results;
    }
}
