package com.dexian.extractor;

import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;
import java.util.regex.*;
import java.io.BufferedReader;
import java.io.InputStreamReader;

public class SecProductionDataExtractor {

    // Flexible label sets
    private static final String[] OIL_TAGS = {
            "Oil Production", "Crude Oil Production", "Oil (MBbls)", "Oil", "Crude oil"
    };
    private static final String[] GAS_TAGS = {
            "Natural Gas Production", "Gas Production", "Natural Gas", "Gas (MMcf)"
    };
    private static final String[] NGL_TAGS = {
            "Natural Gas Liquid Production", "Natural Gas Liquids", "NGL Production",
            "Natural gas liquids", "NGLs", "Natural Gas Liquids Production"
    };
    private static final String[] TOTAL_TAGS = {
            "Total", "Total Production", "Total Equivalent Production",
            "Total (MBOE)", "Total MBoe"
    };

    public static void main(String[] args) {
        // Input CIKs
        String[] ciks = {
                "1658566", "1090012", "1792580", "858470", "821189",
                "893538", "1528129", "1520006", "797468", "34088"
        };

        JSONObject finalData = new JSONObject();

        for (String cik : ciks) {
            System.out.println("🔎 Processing CIK: " + cik);
            try {
                JSONObject cikData = processCompany(cik);
                if (cikData.length() > 0) {
                    finalData.put(cik, cikData);
                }
            } catch (Exception e) {
                System.out.println("❌ Error for CIK " + cik + ": " + e.getMessage());
            }
        }

        System.out.println("\n✅ Final Extracted Data:\n" + finalData.toString(2));
    }

    // =================== MAIN COMPANY PROCESSOR ===================
    private static JSONObject processCompany(String cik) throws Exception {
        JSONObject quarterlyData = new JSONObject();

        List<String> filingUrls = getRecentFilings(cik);
        int quarter = 1;

        for (String url : filingUrls) {
            System.out.println("Fetching " + url);

            Map<String, Double> metrics = extractProductionData(url);
            if (!metrics.isEmpty()) {
                JSONObject qData = new JSONObject(metrics);
                qData.put("source", url);
                quarterlyData.put("Q" + quarter++, qData);
            }
        }

        return quarterlyData;
    }

    // =================== FETCH FILINGS FROM SEC ===================
    private static List<String> getRecentFilings(String cik) throws Exception {
        String api = "https://data.sec.gov/submissions/CIK" + String.format("%010d", Long.parseLong(cik)) + ".json";
        String jsonText = fetchJson(api);
        JSONObject json = new JSONObject(jsonText);

        List<String> urls = new ArrayList<>();
        if (!json.has("filings")) return urls;

        JSONObject recent = json.getJSONObject("filings").getJSONObject("recent");
        if (!recent.has("primaryDocument")) return urls;

        for (int i = 0; i < recent.getJSONArray("primaryDocument").length(); i++) {
            String fileName = recent.getJSONArray("primaryDocument").getString(i);
            if (fileName.endsWith(".htm") && fileName.contains("2025")) {
                String accession = recent.getJSONArray("accessionNumber").getString(i).replace("-", "");
                urls.add("https://www.sec.gov/Archives/edgar/data/" + cik + "/" + accession + "/" + fileName);
                if (urls.size() >= 4) break; // Limit to 4 quarters
            }
        }
        return urls;
    }


    // =================== UTILITIES ===================
    private static String fetchJson(String apiUrl) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(apiUrl).openConnection();
        conn.setRequestProperty("User-Agent", "DataExtractorApp/1.0 (contact: yourname@example.com)");
        conn.setRequestProperty("Accept", "application/json");
        conn.setRequestProperty("Connection", "keep-alive");
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(15000);

        int status = conn.getResponseCode();
        InputStream inputStream = (status >= 200 && status < 300)
                ? conn.getInputStream()
                : conn.getErrorStream();

        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) sb.append(line);
        reader.close();

        // Simple check before parsing JSON
        String response = sb.toString().trim();
        if (!response.startsWith("{")) {
            throw new IOException("Invalid (non-JSON) response from SEC. Status=" + status +
                    ", Body starts with: " + response.substring(0, Math.min(80, response.length())) +
                    ", URL=[" + apiUrl + "]");
        }

        // Add gentle rate limiting
        try { Thread.sleep(1200); } catch (InterruptedException ignored) {}

        return response;
    }

    private static Map<String, Double> extractProductionData(String url) throws Exception {
        Map<String, Double> result = new LinkedHashMap<>();

        // Add email-based user-agent
        Document doc = Jsoup.connect(url)
                .userAgent("MyCompanyDataApp/1.0 (contact: yourname@example.com)")
                .timeout(20000)
                .header("Accept", "text/html")
                .header("Connection", "keep-alive")
                .get();

        Elements tables = doc.select("table");
        for (Element table : tables) {
            Elements rows = table.select("tr");
            for (Element row : rows) {
                Elements cells = row.select("td, th");
                if (cells.size() < 2) continue;

                String header = cells.get(0).text().trim();
                String numericStr = extractNumber(cells.text());
                if (numericStr == null) continue;

                double value = parseDoubleSafe(numericStr);

                if (matchesTag(header, OIL_TAGS)) {
                    result.put("Oil Production", value);
                } else if (matchesTag(header, GAS_TAGS)) {
                    result.put("Natural Gas Production", value);
                } else if (matchesTag(header, NGL_TAGS)) {
                    result.put("Natural Gas Liquid Production", value);
                } else if (matchesTag(header, TOTAL_TAGS)) {
                    result.put("Total", value);
                }
            }
        }

        // Throttle each SEC HTML fetch
        try { Thread.sleep(1500); } catch (InterruptedException ignored) {}

        return result;
    }

    private static boolean matchesTag(String text, String[] patterns) {
        for (String pattern : patterns) {
            if (text.toLowerCase().contains(pattern.toLowerCase())) return true;
        }
        return false;
    }

    private static String extractNumber(String text) {
        Matcher m = Pattern.compile("([0-9]+(?:\\.[0-9]+)?)").matcher(text.replace(",", ""));
        return m.find() ? m.group(1) : null;
    }

    private static double parseDoubleSafe(String val) {
        try {
            return Double.parseDouble(val);
        } catch (Exception e) {
            return 0.0;
        }
    }
}
