package com.dexian.extractor.controller;

import com.dexian.extractor.service.SecExtractorService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/sec")
public class SecExtractorController {

    private final SecExtractorService extractorService;

    public SecExtractorController(SecExtractorService extractorService) {
        this.extractorService = extractorService;
    }

    @PostMapping("/extract")
    public String extractByCik(@RequestParam String cik) {
        return extractorService.extractAndStoreData(cik);
    }
}
