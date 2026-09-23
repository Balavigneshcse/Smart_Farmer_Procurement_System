package com.cropcoders.procurement.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.cropcoders.procurement.dto.CentreResponse;
import com.cropcoders.procurement.dto.CropResponse;
import com.cropcoders.procurement.dto.SlotDayResponse;
import com.cropcoders.procurement.service.CatalogueService;
import com.cropcoders.procurement.service.SlotService;

/** Public information: centres, crops and free slots. No login needed. */
@RestController
@RequestMapping("/api")
public class CatalogueController {

    private final CatalogueService catalogueService;
    private final SlotService slotService;

    public CatalogueController(CatalogueService catalogueService, SlotService slotService) {
        this.catalogueService = catalogueService;
        this.slotService = slotService;
    }

    @GetMapping("/centres")
    public List<CentreResponse> centres() {
        return catalogueService.activeCentres();
    }

    @GetMapping("/crops")
    public List<CropResponse> crops() {
        return catalogueService.activeCrops();
    }

    @GetMapping("/centres/{id}/slots")
    public List<SlotDayResponse> slots(@PathVariable("id") Long centreId) {
        return slotService.upcomingSlots(centreId);
    }
}
