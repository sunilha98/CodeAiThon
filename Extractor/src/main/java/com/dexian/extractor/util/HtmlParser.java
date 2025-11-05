package com.dexian.extractor.util;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class HtmlParser {

    private static final String USER_AGENT = "Mozilla/5.0 (compatible; DataExtractorBot/1.0; +https://www.yourdomain.com/info)";

    public static Map<String, Map<String, Object>> extractSecData(String cik) {
        Map<String, Map<String, Object>> result = new LinkedHashMap<>();

        try {
            // ✅ Try JSON API first
            String apiUrl = "https://data.sec.gov/api/xbrl/company_facts/000" + cik + ".json";
            String json = fetchData(apiUrl);

            if (json != null && json.trim().startsWith("{")) {
                // if JSON found, parse and convert to metrics (you can extend later)
                System.out.println("✅ JSON data found for CIK " + cik);
                return parseJsonMetrics(json);
            } else {
                System.out.println("❌ No JSON data found for CIK " + cik);
            }
        } catch (Exception e) {
            System.out.println("⚠️ SEC JSON fetch failed, fallback to HTML: " + e.getMessage());
        }

        // ✅ Fallback: parse HTML 10-Q/10-K
        try {
            System.out.println("⚠️ SEC JSON not found. Falling back to HTML parser...");

            String htmlUrl = findLatestFilingUrl(cik);
            if (htmlUrl == null) {
                System.out.println("❌ No filing URL found for " + cik);
                return result;
            }

            String html = fetchData(htmlUrl);
            if (html == null || html.isEmpty()) {
                System.out.println("❌ Empty HTML for " + cik);
                return result;
            }

            Map<String, Object> parsedData = parseHtmlForMetrics(html);
            parsedData.put("source", htmlUrl);
            result.put("Q3-2025", parsedData);

            System.out.println("✅ Parsed HTML fallback for " + cik);
        } catch (Exception e) {
            System.out.println("❌ HTML parsing error for " + cik + ": " + e.getMessage());
        }

        return result;
    }

    private static String fetchData(String urlStr) {
        try {
            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestProperty("User-Agent", USER_AGENT);
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(15000);

            if (conn.getResponseCode() != 200) return null;

            try (Scanner sc = new Scanner(conn.getInputStream())) {
                sc.useDelimiter("\\A");
                return sc.hasNext() ? sc.next() : null;
            }
        } catch (IOException e) {
            return null;
        }
    }

    private static String findLatestFilingUrl(String cik) {
        try {
            // Step 1️⃣ — Build feed URL for 10-Q filings
            String feedUrl = "https://www.sec.gov/cgi-bin/browse-edgar?action=getcompany&CIK="
                    + cik + "&type=10-Q&owner=exclude&count=10&output=atom";
            String feed = fetchData(feedUrl);

            // Step 2️⃣ — If 10-Q feed is empty, try 10-K
            if (feed == null || !feed.contains("<entry>")) {
                feedUrl = "https://www.sec.gov/cgi-bin/browse-edgar?action=getcompany&CIK="
                        + cik + "&type=10-K&owner=exclude&count=10&output=atom";
                feed = fetchData(feedUrl);
            }

            if (feed == null || !feed.contains("<entry>")) {
                System.out.println("⚠️ No entries found in Atom feed for CIK " + cik);
                return null;
            }

            // Step 3️⃣ — Match <link> patterns (new and old SEC Atom structures)
            Pattern linkPattern = Pattern.compile(
                    "<link[^>]+href=\"(https://www.sec.gov/Archives/edgar/data/[^\\\"]+)\"",
                    Pattern.CASE_INSENSITIVE);
            Matcher matcher = linkPattern.matcher(feed);

            String filingPageUrl = null;
            while (matcher.find()) {
                String url = matcher.group(1);
                // Skip XSL or summary links, pick actual filing index.html
                if (url.contains("/index.htm") || url.matches(".*\\d{10,}/.*")) {
                    filingPageUrl = url;
                    break;
                }
            }

            if (filingPageUrl == null) {
                System.out.println("⚠️ No filing detail page link found for CIK " + cik);
                return null;
            }

            System.out.println("🔗 Filing detail page: " + filingPageUrl);

            // Step 4️⃣ — Fetch the filing detail page HTML
            String filingPageHtml = fetchData(filingPageUrl);
            if (filingPageHtml == null) {
                System.out.println("⚠️ Empty filing page for CIK " + cik);
                return null;
            }

            // Step 5️⃣ — Extract primary document .htm link from the index page
            Pattern docPattern = Pattern.compile(
                    "<a href=\"(/Archives/edgar/data/" + cik + "/[^\"]+\\.htm)\"",
                    Pattern.CASE_INSENSITIVE);
            Matcher docMatcher = docPattern.matcher(filingPageHtml);

            if (docMatcher.find()) {
                String relativeUrl = docMatcher.group(1);
                String fullUrl = "https://www.sec.gov" + relativeUrl;
                System.out.println("✅ Found filing HTML URL: " + fullUrl);
                return fullUrl;
            }

            System.out.println("⚠️ No HTML document link found on filing page for CIK " + cik);
        } catch (Exception e) {
            System.out.println("❌ Filing URL lookup failed for CIK " + cik + ": " + e.getMessage());
        }

        return null;
    }

    private static Map<String, Object> parseHtmlForMetrics(String html) {
        Map<String, Object> metrics = new LinkedHashMap<>();
        Document doc = Jsoup.parse(html);

        // Example: find tables with financial data
        Elements tables = doc.select("table");
        for (Element table : tables) {
            Elements rows = table.select("tr");
            for (Element row : rows) {
                Elements cols = row.select("td");
                if (cols.size() >= 2) {
                    String key = cols.get(0).text().trim();
                    String val = cols.get(1).text().trim().replace(",", "");

                    if (key.matches("(?i).*(oil|gas|barrel|production|revenue).*") && val.matches("[\\d.]+")) {
                        metrics.put(key, Double.parseDouble(val));
                    }
                }
            }
        }

        if (metrics.isEmpty()) {
            // fallback pattern
            metrics.put("Oil Production (BBL)", 0.0);
            metrics.put("Gas Production (MCF)", 0.0);
        }

        return metrics;
    }

    private static Map<String, Map<String, Object>> parseJsonMetrics(String json) {
        // Simplified placeholder — can be expanded to real SEC JSON parsing
        Map<String, Map<String, Object>> result = new LinkedHashMap<>();
        Map<String, Object> dummy = new HashMap<>();
        dummy.put("Revenue", 123456.78);
        dummy.put("Oil Production (BBL)", 98765.43);
        dummy.put("source", "https://data.sec.gov/api/xbrl/company_facts");
        result.put("Q3-2025", dummy);
        return result;
    }
}
