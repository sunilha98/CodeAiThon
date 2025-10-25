package com.dexian.extractor.controller;

import com.dexian.extractor.clients.EDGARClient;
import com.dexian.extractor.model.RevenueRecord;
import com.dexian.extractor.service.ExtractionService;
import com.dexian.extractor.service.RevenueService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/extract")
@RequiredArgsConstructor
public class ExtractionController {

    private final ExtractionService service;
    private final RevenueService revenueService;
    private final EDGARClient edgarClient;

    @GetMapping("/revenue")
    public void extractRevenueMetrics(@RequestParam String cik, HttpServletResponse response) {
        try {
            String htmlContent = edgarClient.fetchLatest10QHtml(cik);

            if (htmlContent == null || htmlContent.isEmpty()) {
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                response.getWriter().write("No 10-Q filing found for CIK: " + cik);
                return;
            }

            response.setContentType("text/html;charset=UTF-8");
            response.getWriter().write(htmlContent);
            response.getWriter().flush();
        } catch (Exception e) {
            e.printStackTrace();
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }

    @GetMapping("/{cik}")
    public ResponseEntity<List<RevenueRecord>> extractRevenue(@PathVariable String cik, @RequestParam String company) {
        List<RevenueRecord> records= revenueService.extractRecentUsGaapRevenueFacts(cik, company);
        return ResponseEntity.ok(records);
    }

//    @GetMapping("/{cik}")
//    public List<RevenueRecord> getRevenues(@PathVariable String cik) {
//        return revenueService.getRevenues(cik);
//    }

}
