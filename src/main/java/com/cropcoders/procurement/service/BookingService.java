package com.cropcoders.procurement.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cropcoders.procurement.config.AppProperties;
import com.cropcoders.procurement.dto.BookingEventResponse;
import com.cropcoders.procurement.dto.BookingResponse;
import com.cropcoders.procurement.dto.CreateBookingRequest;
import com.cropcoders.procurement.entity.Booking;
import com.cropcoders.procurement.entity.BookingEvent;
import com.cropcoders.procurement.entity.Crop;
import com.cropcoders.procurement.entity.Slot;
import com.cropcoders.procurement.enums.BookingEventType;
import com.cropcoders.procurement.enums.BookingStatus;
import com.cropcoders.procurement.exception.ApiException;
import com.cropcoders.procurement.repository.BookingEventRepository;
import com.cropcoders.procurement.repository.BookingRepository;
import com.cropcoders.procurement.repository.CropRepository;
import com.cropcoders.procurement.repository.SlotRepository;
import com.cropcoders.procurement.repository.UserRepository;

/** A farmer booking, viewing and cancelling their slots. */
@Service
public class BookingService {

    private final BookingRepository bookingRepository;
    private final BookingEventRepository eventRepository;
    private final SlotRepository slotRepository;
    private final CropRepository cropRepository;
    private final UserRepository userRepository;
    private final TokenService tokenService;
    private final int daysAhead;

    public BookingService(BookingRepository bookingRepository, BookingEventRepository eventRepository,
                          SlotRepository slotRepository, CropRepository cropRepository, UserRepository userRepository,
                          TokenService tokenService, AppProperties properties) {
        this.bookingRepository = bookingRepository;
        this.eventRepository = eventRepository;
        this.slotRepository = slotRepository;
        this.cropRepository = cropRepository;
        this.userRepository = userRepository;
        this.tokenService = tokenService;
        this.daysAhead = properties.booking().daysAhead();
    }

    /**
     * Books a slot. Everything happens in one transaction: if any step fails
     * (slot full, duplicate booking, ...) the place and token are not used up.
     */
    @Transactional
    public BookingResponse create(Long farmerId, CreateBookingRequest request) {
        Slot slot = slotRepository.findWithCentreById(request.slotId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "SLOT_NOT_FOUND", "Slot not found."));
        Crop crop = cropRepository.findById(request.cropId())
                .filter(Crop::isActive)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "CROP_NOT_FOUND", "Crop not found."));

        LocalDateTime now = LocalDateTime.now();
        LocalDate lastBookableDay = now.toLocalDate().plusDays(daysAhead - 1L);
        if (slot.hasEnded(now) || slot.getSlotDate().isAfter(lastBookableDay) || !slot.getCentre().isActive()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "SLOT_CLOSED", "This time can no longer be booked.");
        }
        if (bookingRepository.existsByFarmerIdAndBookingDateAndStatusIn(farmerId, slot.getSlotDate(),
                BookingStatus.ACTIVE)) {
            throw new ApiException(HttpStatus.CONFLICT, "ACTIVE_BOOKING_EXISTS",
                    "You already have a booking on this day. Cancel it first to book another time.");
        }
        if (slotRepository.reserveSeat(slot.getId()) == 0) {
            throw new ApiException(HttpStatus.CONFLICT, "SLOT_FULL",
                    "Sorry, this time just got full. Please choose another time.");
        }

        String token = tokenService.nextToken(slot.getCentre(), slot.getSlotDate());
        Booking booking = bookingRepository.save(
                new Booking(token, userRepository.getReferenceById(farmerId), slot, crop, request.estQtyQtl()));
        BookingEvent event = eventRepository.save(new BookingEvent(booking, BookingEventType.BOOKED,
                "Slot " + slot.getStartTime() + "-" + slot.getEndTime(), farmerId));

        return BookingResponse.from(booking, now, List.of(BookingEventResponse.from(event)));
    }

    @Transactional(readOnly = true)
    public List<BookingResponse> myBookings(Long farmerId) {
        LocalDateTime now = LocalDateTime.now();
        return bookingRepository.findAllForFarmer(farmerId).stream()
                .map(booking -> BookingResponse.from(booking, now, null))
                .toList();
    }

    @Transactional(readOnly = true)
    public BookingResponse myBooking(Long farmerId, Long bookingId) {
        return withTimeline(find(farmerId, bookingId));
    }

    /** Cancels a booking that has not started yet, and gives the place back to the slot. */
    @Transactional
    public BookingResponse cancel(Long farmerId, Long bookingId) {
        Booking booking = find(farmerId, bookingId);
        if (!booking.isCancellable(LocalDateTime.now())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "BOOKING_NOT_CANCELLABLE",
                    "This booking can no longer be cancelled.");
        }
        booking.setStatus(BookingStatus.CANCELLED);
        slotRepository.releaseSeat(booking.getSlot().getId());
        eventRepository.save(new BookingEvent(booking, BookingEventType.CANCELLED, "Cancelled by farmer", farmerId));
        return withTimeline(booking);
    }

    private Booking find(Long farmerId, Long bookingId) {
        return bookingRepository.findForFarmer(bookingId, farmerId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "BOOKING_NOT_FOUND", "Booking not found."));
    }

    private BookingResponse withTimeline(Booking booking) {
        List<BookingEventResponse> events = eventRepository.findByBookingIdOrderByCreatedAtAscIdAsc(booking.getId())
                .stream()
                .map(BookingEventResponse::from)
                .toList();
        return BookingResponse.from(booking, LocalDateTime.now(), events);
    }
}
