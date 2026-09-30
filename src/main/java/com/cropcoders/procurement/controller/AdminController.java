package com.cropcoders.procurement.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
import com.cropcoders.procurement.dto.NewCentreRequest;
import com.cropcoders.procurement.security.AccessScope;
import com.cropcoders.procurement.security.AuthUser;
import com.cropcoders.procurement.service.AdminCatalogueService;
import com.cropcoders.procurement.service.AdminDashboardService;
import com.cropcoders.procurement.service.DemoDataService;

import jakarta.validation.Valid;

/**
 * Admin pages: dashboard numbers, centre and crop settings, demo data.
 * Roles ADMIN and SUPER_ADMIN (see SecurityConfig). A centre admin only sees and changes their own
 * centre; adding centres, crop rates and demo data are for the super admin.
 */
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
    public AdminDashboardResponse dashboard(@AuthenticationPrincipal AuthUser me) {
        return dashboardService.dashboard(AccessScope.centreOf(me));
    }

    // ---------- Crops (same rates for every centre: only the super admin changes them) ----------

    @GetMapping("/crops")
    public List<AdminCropView> crops() {
        return catalogueService.crops();
    }

    @PostMapping("/crops")
    @ResponseStatus(HttpStatus.CREATED)
    public AdminCropView addCrop(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody CropRequest request) {
        AccessScope.requireSuperAdmin(me);
        return catalogueService.addCrop(request);
    }

    @PutMapping("/crops/{id}")
    public AdminCropView updateCrop(@AuthenticationPrincipal AuthUser me, @PathVariable("id") Long id,
                                    @Valid @RequestBody CropRequest request) {
        AccessScope.requireSuperAdmin(me);
        return catalogueService.updateCrop(id, request);
    }

    // ---------- Centres ----------

    @GetMapping("/centres")
    public List<AdminCentreView> centres(@AuthenticationPrincipal AuthUser me) {
        return catalogueService.centres(AccessScope.centreOf(me));
    }

    @PostMapping("/centres")
    @ResponseStatus(HttpStatus.CREATED)
    public AdminCentreView addCentre(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody NewCentreRequest request) {
        AccessScope.requireSuperAdmin(me);
        return catalogueService.addCentre(request);
    }

    @PutMapping("/centres/{id}")
    public AdminCentreView updateCentre(@AuthenticationPrincipal AuthUser me, @PathVariable("id") Long id,
                                        @Valid @RequestBody CentreRequest request) {
        AccessScope.requireCentre(me, id);
        return catalogueService.updateCentre(id, request);
    }

    // ---------- Demo data (all centres at once: super admin) ----------

    @GetMapping("/demo")
    public DemoDataResponse demoStatus(@AuthenticationPrincipal AuthUser me) {
        AccessScope.requireSuperAdmin(me);
        return demoDataService.status();
    }

    @PostMapping("/demo")
    public DemoDataResponse loadDemo(@AuthenticationPrincipal AuthUser me) {
        AccessScope.requireSuperAdmin(me);
        return demoDataService.load();
    }

    @DeleteMapping("/demo")
    public DemoDataResponse removeDemo(@AuthenticationPrincipal AuthUser me) {
        AccessScope.requireSuperAdmin(me);
        return demoDataService.remove();
    }
}
