package com.dexian.extractor.clients;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class SECDataFetcher {

    private static final String CIK = "001090012"; // Devon Energy
    private static final String USER_AGENT = "Your Company Name Contact@Email.com";

    public static void main(String[] args) {
        try {
            // STEP 1: Find the latest quarterly (or annual, as fallback) filing.
            FilingDetails latestFiling = getLatestCompliantFiling(CIK);

            if (latestFiling == null) {
                System.err.println("❌ CRITICAL ERROR: Could not find ANY major financial filing (10-Q or 10-K) for CIK: " + CIK + " in the SEC's recent submissions.");
                return;
            }

            System.out.println("✅ Step 1 Success: Found the most recent financial filing (" + latestFiling.formType + ") filed on " + latestFiling.filingDate + " for period ending " + latestFiling.periodEnd);

            // STEP 2: Fetch the actual structured revenue data from the SEC API
            String revenueMetric = fetchStructuredMetric(CIK, latestFiling.periodEnd, latestFiling.filingDate);

            System.out.println("\n--- REAL EXTRACTED FINANCIAL DATA ---");
            if (revenueMetric != null) {
                System.out.println(revenueMetric);
            } else {
                System.err.println("❌ ERROR: Could not extract Real Revenue data for the specified period (" + latestFiling.periodEnd + ").");
            }

        } catch (Exception e) {
            System.err.println("An unexpected error occurred during extraction:");
            e.printStackTrace();
        }
    }

    private static class FilingDetails {
        String formType;
        String filingDate;
        String periodEnd;
    }

    //-------------------------------------------------------------------------
    // STEP 1: FIND LATEST COMPLIANT FILING (INCLUDING 10-K FALLBACK)
    //-------------------------------------------------------------------------

    /**
     * Finds the absolute latest financial filing by prioritizing 10-Q, then falling
     * back to common 10-K annual reports.
     */
    private static FilingDetails getLatestCompliantFiling(String cik) throws IOException, InterruptedException {
        // CIK padding for the /submissions/ endpoint
        String paddedCik = String.format("%010d", Integer.parseInt(cik.replaceFirst("^0+(?!$)", "")));
        String submissionsApiUrl = String.format("https://data.sec.gov/submissions/CIK%s.json", paddedCik);

        HttpClient client = HttpClient.newHttpClient();
        ObjectMapper mapper = new ObjectMapper();

        HttpResponse<String> response = sendRequest(submissionsApiUrl);

        if (response.statusCode() != 200) {
            System.err.println("SEC API call failed with status code: " + response.statusCode());
            return null;
        }

        JsonNode root = mapper.readTree(response.body());
        JsonNode recentFilings = root.path("filings").path("recent");

        // FINAL FIX: Expanded list prioritizing 10-Q variants, then falling back to 10-K variants
        List<String> formsToTry = List.of("10-Q", "10-Q/A", "10-QT", "10-Q/A1", "10-Q/A2", "10-K", "10-K/A");

        if (!recentFilings.has("form") || !recentFilings.get("form").isArray()) {
            System.err.println("SEC JSON structure is missing the 'form' array in recent filings.");
            return null;
        }

        // The SEC's 'recent' filings are generally listed chronologically (most recent first)
        for (String formType : formsToTry) {
            for (int i = 0; i < recentFilings.get("form").size(); i++) {

                JsonNode currentFormNode = recentFilings.path("form").get(i);
                if (currentFormNode == null || !formType.equals(currentFormNode.asText())) {
                    continue;
                }

                // Ensure required fields exist
                JsonNode filingDateNode = recentFilings.path("filingDate").get(i);
                JsonNode periodEndNode = recentFilings.path("periodOfReport").get(i);

                if (filingDateNode == null || periodEndNode == null || filingDateNode.isNull() || periodEndNode.isNull()) {
                    continue;
                }

                // If found, return the details immediately.
                FilingDetails details = new FilingDetails();
                details.formType = formType;
                details.filingDate = filingDateNode.asText();

                // periodOfReport is YYYYMMDD. Format it to YYYY-MM-DD
                String periodOfReport = periodEndNode.asText();
                details.periodEnd = periodOfReport.substring(0, 4) + "-" + periodOfReport.substring(4, 6) + "-" + periodOfReport.substring(6, 8);

                return details;
            }
        }
        return null;
    }

    //-------------------------------------------------------------------------
    // STEP 2: FETCH REAL STRUCTURED DATA (REVENUE)
    //-------------------------------------------------------------------------

    private static String fetchStructuredMetric(String cik, String periodEnd, String filingDate) throws IOException, InterruptedException {
        String unpaddedCik = cik.replaceFirst("^0+(?!$)", "");
        String conceptApiUrl = String.format(
                "https://data.sec.gov/api/xbrl/companyconcept/CIK%s/us-gaap/Revenues.json",
                unpaddedCik
        );

        HttpClient client = HttpClient.newHttpClient();
        ObjectMapper mapper = new ObjectMapper();

        HttpResponse<String> response = sendRequest(conceptApiUrl);

        if (response.statusCode() != 200) {
            System.err.println("Concept API failed with status code: " + response.statusCode());
            return null;
        }

        JsonNode root = mapper.readTree(response.body());
        JsonNode facts = root.path("units").path("USD");

        for (JsonNode fact : facts) {
            if (fact.has("end") && fact.get("end").asText().equals(periodEnd)) {

                long value = fact.get("val").asLong();
                String unit = fact.get("unit").asText();

                String formattedValue = String.format("%,.2f Billion %s", (double)value / 1_000_000_000L, unit);

                return String.format(
                        "Metric: Total Revenue (us-gaap:Revenues)\n" +
                                "  Value: %s\n" +
                                "  Period End: %s\n" +
                                "  Filing Date: %s",
                        formattedValue, periodEnd, filingDate
                );
            }
        }

        return null;
    }

    /**
     * Helper function to send the HTTP request with the required User-Agent.
     */
    private static HttpResponse<String> sendRequest(String url) throws IOException, InterruptedException {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", USER_AGENT)
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }
}