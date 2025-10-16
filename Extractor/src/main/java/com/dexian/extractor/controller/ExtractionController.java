package com.dexian.extractor.controller;

import com.dexian.extractor.clients.EDGARClient;
import com.dexian.extractor.service.ExtractionService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/extract")
@RequiredArgsConstructor
public class ExtractionController {

    private final ExtractionService service;
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

}
