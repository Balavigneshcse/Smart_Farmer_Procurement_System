package com.cropcoders.procurement.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cropcoders.procurement.dto.CentreResponse;
import com.cropcoders.procurement.dto.CropResponse;
import com.cropcoders.procurement.repository.CentreRepository;
import com.cropcoders.procurement.repository.CropRepository;

/** Public master data: the centres farmers can visit and the crops they can sell. */
@Service
@Transactional(readOnly = true)
public class CatalogueService {

    private final CentreRepository centreRepository;
    private final CropRepository cropRepository;

    public CatalogueService(CentreRepository centreRepository, CropRepository cropRepository) {
        this.centreRepository = centreRepository;
        this.cropRepository = cropRepository;
    }

    public List<CentreResponse> activeCentres() {
        return centreRepository.findByActiveTrueOrderByName().stream()
                .map(CentreResponse::from)
                .toList();
    }

    public List<CropResponse> activeCrops() {
        return cropRepository.findByActiveTrueOrderByNameEn().stream()
                .map(CropResponse::from)
                .toList();
    }
}
