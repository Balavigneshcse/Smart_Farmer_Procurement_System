package com.cropcoders.procurement.service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cropcoders.procurement.dto.AdminCentreView;
import com.cropcoders.procurement.dto.AdminCropView;
import com.cropcoders.procurement.dto.CentreRequest;
import com.cropcoders.procurement.dto.CropRequest;
import com.cropcoders.procurement.entity.Centre;
import com.cropcoders.procurement.entity.Crop;
import com.cropcoders.procurement.exception.ApiException;
import com.cropcoders.procurement.realtime.QueueChangedEvent;
import com.cropcoders.procurement.repository.CentreRepository;
import com.cropcoders.procurement.repository.CropRepository;

/**
 * The admin changes crops (rates, moisture limits) and centres (counters, hours, service time).
 * A new rate applies to weighments from now on; weighments already saved keep the rate they used.
 */
@Service
@Transactional
public class AdminCatalogueService {

    private final CropRepository cropRepository;
    private final CentreRepository centreRepository;
    private final SlotService slotService;
    private final JdbcTemplate jdbc;
    private final ApplicationEventPublisher events;

    public AdminCatalogueService(CropRepository cropRepository, CentreRepository centreRepository,
                                 SlotService slotService, JdbcTemplate jdbc, ApplicationEventPublisher events) {
        this.cropRepository = cropRepository;
        this.centreRepository = centreRepository;
        this.slotService = slotService;
        this.jdbc = jdbc;
        this.events = events;
    }

    // ---------- Crops ----------

    @Transactional(readOnly = true)
    public List<AdminCropView> crops() {
        return cropRepository.findAll().stream()
                .sorted(Comparator.comparing(Crop::getNameEn))
                .map(AdminCropView::from)
                .toList();
    }

    public AdminCropView addCrop(CropRequest request) {
        String nameEn = request.nameEn().trim();
        ensureCropNameFree(nameEn, null);
        Crop crop = new Crop(nameEn, request.nameTa().trim(), request.nameHi().trim(), request.ratePerQtl(),
                request.maxMoisturePct());
        crop.setActive(request.active());
        return AdminCropView.from(cropRepository.save(crop));
    }

    public AdminCropView updateCrop(Long id, CropRequest request) {
        Crop crop = cropRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "CROP_NOT_FOUND", "Crop not found."));
        String nameEn = request.nameEn().trim();
        ensureCropNameFree(nameEn, id);
        crop.setNameEn(nameEn);
        crop.setNameTa(request.nameTa().trim());
        crop.setNameHi(request.nameHi().trim());
        crop.setRatePerQtl(request.ratePerQtl());
        crop.setMaxMoisturePct(request.maxMoisturePct());
        crop.setActive(request.active());
        return AdminCropView.from(crop);
    }

    private void ensureCropNameFree(String nameEn, Long exceptId) {
        boolean taken = cropRepository.findAll().stream()
                .anyMatch(c -> c.getNameEn().equalsIgnoreCase(nameEn) && !c.getId().equals(exceptId));
        if (taken) {
            throw new ApiException(HttpStatus.CONFLICT, "CROP_EXISTS", "A crop called " + nameEn + " already exists.");
        }
    }

    // ---------- Centres ----------

    @Transactional(readOnly = true)
    public List<AdminCentreView> centres() {
        return centreRepository.findAll().stream()
                .sorted(Comparator.comparing(Centre::getName))
                .map(AdminCentreView::from)
                .toList();
    }

    /**
     * Saves the new settings, then updates the slots: capacity of slots not started yet, removes empty
     * slots outside the new hours and creates slots for new hours (SlotService.applyCentreSettings).
     */
    public AdminCentreView updateCentre(Long id, CentreRequest request) {
        Centre centre = centreRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "CENTRE_NOT_FOUND", "Centre not found."));
        validateHours(centre, request.openTime(), request.closeTime());

        int slotMinutes = centre.getSlotMinutes();
        if (request.counters() * slotMinutes / request.avgServiceMin() < 1) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "NO_CAPACITY",
                    "With these numbers a " + slotMinutes + "-minute slot would hold no farmers. "
                            + "Add counters or lower the service time.");
        }
        Integer busiestCounter = jdbc.queryForObject("""
                SELECT max(counter_no) FROM bookings
                WHERE centre_id = ? AND booking_date = ? AND status = 'CALLED'""",
                Integer.class, id, LocalDate.now());
        if (busiestCounter != null && busiestCounter > request.counters()) {
            throw new ApiException(HttpStatus.CONFLICT, "COUNTER_IN_USE",
                    "Counter " + busiestCounter + " is serving a farmer right now. Finish that farmer first.",
                    Map.of("counter", busiestCounter));
        }

        centre.setName(request.name().trim());
        centre.setDistrict(request.district().trim());
        centre.setAddress(request.address().trim());
        centre.setOpenTime(request.openTime());
        centre.setCloseTime(request.closeTime());
        centre.setCounters(request.counters());
        centre.setAvgServiceMin(request.avgServiceMin());
        centre.setActive(request.active());
        centreRepository.flush();   // slot SQL below must see the new values

        slotService.applyCentreSettings(centre);
        events.publishEvent(new QueueChangedEvent(centre.getId()));   // TV and desk show the new counters
        return AdminCentreView.from(centre);
    }

    /** Hours must fit whole slots and fall on slot boundaries (e.g. :00 or :30 for 30-minute slots). */
    private static void validateHours(Centre centre, LocalTime open, LocalTime close) {
        int length = centre.getSlotMinutes();
        int openMin = open.toSecondOfDay() / 60;
        int closeMin = close.toSecondOfDay() / 60;
        if (open.getSecond() != 0 || close.getSecond() != 0 || openMin % length != 0 || closeMin % length != 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "HOURS_NOT_ON_SLOT",
                    "Opening and closing times must be on a " + length + "-minute boundary (for example 8:00 or 8:30).");
        }
        if (closeMin - openMin < length) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "HOURS_TOO_SHORT",
                    "The centre must be open for at least one " + length + "-minute slot.");
        }
    }
}
