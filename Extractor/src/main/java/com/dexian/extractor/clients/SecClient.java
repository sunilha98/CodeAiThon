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
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

@Service
public class SecClient {

    private static final String USER_AGENT =
            "DexianCodeAIThon QuantumCoders (contact: sunilha98@gmail.com)";

    private static final String COMPANY_FACTS_URL =
            "https://data.sec.gov/api/xbrl/companyfacts/CIK%010d.json";

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

    public Set<String> getAvailableTags(String cik) {
        Set<String> tags = new HashSet<>();
        try {
            long cikNum = Long.parseLong(cik);
            String url = String.format(COMPANY_FACTS_URL, cikNum);
            System.out.printf("[INFO] Fetching company facts for CIK %s%n", cik);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(30))
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            int status = response.statusCode();

            if (status != 200) {
                System.err.printf("[WARN] SEC returned %d for %s%n", status, url);
                return tags;
            }

            JsonNode root = mapper.readTree(response.body());
            JsonNode facts = root.path("facts");

            if (facts.isMissingNode()) {
                System.err.println("[WARN] No 'facts' section found.");
                return tags;
            }

            Iterator<String> taxonomyIterator = facts.fieldNames();
            while (taxonomyIterator.hasNext()) {
                String taxonomy = taxonomyIterator.next();
                JsonNode taxonomyNode = facts.get(taxonomy);
                Iterator<String> conceptIterator = taxonomyNode.fieldNames();

                while (conceptIterator.hasNext()) {
                    String concept = conceptIterator.next();
                    tags.add(taxonomy + ":" + concept);
                }
            }

            System.out.printf("[OK] Found %d tags for CIK %s%n", tags.size(), cik);
        } catch (IOException | InterruptedException e) {
            System.err.printf("[ERROR] Failed to fetch tags for CIK %s: %s%n", cik, e.getMessage());
        }
        return tags;
    }

    public JsonNode fetchAllCompanyFacts(String cik) {
        try {
            long cikNum = Long.parseLong(cik);
            String url = String.format(COMPANY_FACTS_URL, cikNum);
            System.out.printf("[INFO] Fetching ALL company facts for CIK %s%n", cik);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(60))
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                return mapper.readTree(response.body());
            } else {
                System.err.printf("[WARN] SEC returned %d for %s%n", response.statusCode(), url);
                return null;
            }
        } catch (Exception e) {
            System.err.printf("[ERROR] Failed to fetch company facts for CIK %s: %s%n", cik, e.getMessage());
            return null;
        }
    }


}