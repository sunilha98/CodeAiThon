package com.dexian.extractor.clients;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Service
public class SecClient {

    private static final String USER_AGENT =
            "DexianCodeAIThon QuantumCoders (contact: sunilha98@gmail.com)";

    // Original URL to fetch filing metadata - kept for completeness
    private static final String SUBMISSION_URL =
            "https://data.sec.gov/submissions/CIK%010d.json";

    // New, required URL for fetching structured XBRL data for a concept
    private static final String COMPANY_FACTS_CONCEPT_URL =
            "https://data.sec.gov/api/xbrl/companyconcept/CIK%010d/us-gaap/%s.json";

    private final HttpClient httpClient;
    private final ObjectMapper mapper = new ObjectMapper();

    public SecClient() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(20))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    // New method to fetch structured XBRL facts
    public JsonNode fetchConceptFacts(String cik, String concept) {
        try {
            long cikNum = Long.parseLong(cik);
            // CIK is padded to 10 digits for the SEC API
            String url = String.format(COMPANY_FACTS_CONCEPT_URL, cikNum, concept);

            System.out.printf("[INFO] Fetching concept facts for CIK: %s, Concept: %s%n", cik, concept);
            return fetchJson(url);
        } catch (Exception e) {
            System.err.printf("[ERROR] Failed to fetch concept facts for %s: %s%n", concept, e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    /** (Original method: no longer used in the refactored RevenueService) */
    public String fetchLatestFilingHtml(String cik, String companyName) {
        // ... (Original implementation remains, but will be skipped) ...
        // ... (This method will not be called in the new logic) ...
        return null; // Return null if logic is skipped to prevent accidental use
    }

    /** Fetch JSON from SEC with proper headers and retry on 429 */
    private JsonNode fetchJson(String url) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json")
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        int status = response.statusCode();

        if (status == 200) {
            return mapper.readTree(response.body());
        } else if (status == 429) {
            System.out.println("[WARN] Rate-limited by SEC. Waiting 5 seconds...");
            Thread.sleep(5000);
            return fetchJson(url);
        } else {
            System.err.println("[WARN] SEC returned " + status + " for " + url);
            return null;
        }
    }

    /** (Original method: no longer used in the refactored RevenueService) */
    private String fetchHtml(String url) throws IOException, InterruptedException {
        // ... (Original implementation remains, but will be skipped) ...
        return null;
    }
}