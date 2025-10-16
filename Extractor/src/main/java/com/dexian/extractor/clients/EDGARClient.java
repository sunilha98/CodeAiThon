package com.dexian.extractor.clients;

import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

@Component
public class EDGARClient {

    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/118.0.5993.90 Safari/537.36 " +
                    "(contact: sunilha98@gmail.com)";

    public String fetchLatest10QHtml(String cik) {
        try {
            String normalizedCik = String.format("%010d", Long.parseLong(cik));
            URL url = new URL("https://data.sec.gov/submissions/CIK" + normalizedCik + ".json");

            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestProperty("User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                            "(KHTML, like Gecko) Chrome/118.0.5993.90 Safari/537.36 (contact: sunilha98@gmail.com)");
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestMethod("GET");

            BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            StringBuilder jsonBuilder = new StringBuilder();
            String line;
            while ((line = in.readLine()) != null) {
                jsonBuilder.append(line);
            }
            in.close();

            JSONObject json = new JSONObject(jsonBuilder.toString());
            JSONObject recent = json.getJSONObject("filings").getJSONObject("recent");

            JSONArray forms = recent.getJSONArray("form");
            JSONArray accessionNumbers = recent.getJSONArray("accessionNumber");
            JSONArray primaryDocuments = recent.getJSONArray("primaryDocument");

            for (int i = 0; i < forms.length(); i++) {
                if (forms.getString(i).equalsIgnoreCase("10-Q")) {
                    String accession = accessionNumbers.getString(i).replaceAll("-", "");
                    String doc = primaryDocuments.getString(i);

                    // Use Long.parseLong to strip leading zeros for folder path:
                    String cikFolder = String.valueOf(Long.parseLong(cik));

                    String htmlUrl = "https://www.sec.gov/Archives/edgar/data/" +
                            cikFolder + "/" + accession + "/" + doc;

                    System.out.println("Fetching document: " + htmlUrl);

                    HttpURLConnection docConn = (HttpURLConnection) new URL(htmlUrl).openConnection();
                    docConn.setRequestProperty("User-Agent",
                            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                                    "(KHTML, like Gecko) Chrome/118.0.5993.90 Safari/537.36 (contact: sunilha98@gmail.com)");
                    docConn.setRequestMethod("GET");

                    BufferedReader reader = new BufferedReader(new InputStreamReader(docConn.getInputStream()));
                    StringBuilder htmlBuilder = new StringBuilder();
                    while ((line = reader.readLine()) != null) {
                        htmlBuilder.append(line).append("\n");
                    }
                    reader.close();

                    String htmlContent = htmlBuilder.toString();
                    System.out.println("Fetched document length: " + htmlContent.length());
                    // Return full HTML content here:
                    return htmlContent;
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }

}
