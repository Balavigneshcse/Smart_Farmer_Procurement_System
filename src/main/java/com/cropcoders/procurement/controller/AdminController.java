package com.cropcoders.procurement.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.cropcoders.procurement.dto.AdminCentreView;
import com.cropcoders.procurement.dto.AdminCropView;
import com.cropcoders.procurement.dto.AdminDashboardResponse;
import com.cropcoders.procurement.dto.CentreRequest;
import com.cropcoders.procurement.dto.CropRequest;
import com.cropcoders.procurement.dto.DemoDataResponse;
import com.cropcoders.procurement.service.AdminCatalogueService;
import com.cropcoders.procurement.service.AdminDashboardService;
import com.cropcoders.procurement.service.DemoDataService;

import jakarta.validation.Valid;

/** Admin pages: dashboard numbers, centre and crop settings, demo data. Only role ADMIN (see SecurityConfig). */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminDashboardService dashboardService;
    private final AdminCatalogueService catalogueService;
    private final DemoDataService demoDataService;

    public AdminController(AdminDashboardService dashboardService, AdminCatalogueService catalogueService,
                           DemoDataService demoDataService) {
        this.dashboardService = dashboardService;
        this.catalogueService = catalogueService;
        this.demoDataService = demoDataService;
    }

    @GetMapping("/dashboard")
    public AdminDashboardResponse dashboard() {
        return dashboardService.dashboard();
    }

    // ---------- Crops ----------

    @GetMapping("/crops")
    public List<AdminCropView> crops() {
        return catalogueService.crops();
    }

    @PostMapping("/crops")
    @ResponseStatus(HttpStatus.CREATED)
    public AdminCropView addCrop(@Valid @RequestBody CropRequest request) {
        return catalogueService.addCrop(request);
    }

    @PutMapping("/crops/{id}")
    public AdminCropView updateCrop(@PathVariable("id") Long id, @Valid @RequestBody CropRequest request) {
        return catalogueService.updateCrop(id, request);
    }

    // ---------- Centres ----------

    @GetMapping("/centres")
    public List<AdminCentreView> centres() {
        return catalogueService.centres();
    }

    @PutMapping("/centres/{id}")
    public AdminCentreView updateCentre(@PathVariable("id") Long id, @Valid @RequestBody CentreRequest request) {
        return catalogueService.updateCentre(id, request);
    }

    // ---------- Demo data ----------

    @GetMapping("/demo")
    public DemoDataResponse demoStatus() {
        return demoDataService.status();
    }

    @PostMapping("/demo")
    public DemoDataResponse loadDemo() {
        return demoDataService.load();
    }

    @DeleteMapping("/demo")
    public DemoDataResponse removeDemo() {
        return demoDataService.remove();
    }
}
