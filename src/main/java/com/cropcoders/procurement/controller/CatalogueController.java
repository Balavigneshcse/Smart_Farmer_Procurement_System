package com.cropcoders.procurement.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.cropcoders.procurement.dto.CentreResponse;
import com.cropcoders.procurement.dto.CropResponse;
import com.cropcoders.procurement.service.CatalogueService;

@RestController
@RequestMapping("/api")
public class CatalogueController {

    private final CatalogueService catalogueService;

    public CatalogueController(CatalogueService catalogueService) {
        this.catalogueService = catalogueService;
    }

    @GetMapping("/centres")
    public List<CentreResponse> centres() {
        return catalogueService.activeCentres();
    }

    @GetMapping("/crops")
    public List<CropResponse> crops() {
        return catalogueService.activeCrops();
    }
}
