package com.cropcoders.procurement.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.cropcoders.procurement.dto.BookingResponse;
import com.cropcoders.procurement.dto.CreateBookingRequest;
import com.cropcoders.procurement.security.AuthUser;
import com.cropcoders.procurement.service.BookingService;

import jakarta.validation.Valid;

/** The logged-in farmer's bookings. Only role FARMER can call these (see SecurityConfig). */
@RestController
@RequestMapping("/api/bookings")
public class BookingController {

    private final BookingService bookingService;

    public BookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BookingResponse create(@AuthenticationPrincipal AuthUser farmer,
                                  @Valid @RequestBody CreateBookingRequest request) {
        return bookingService.create(farmer.id(), request);
    }

    @GetMapping("/my")
    public List<BookingResponse> myBookings(@AuthenticationPrincipal AuthUser farmer) {
        return bookingService.myBookings(farmer.id());
    }

    @GetMapping("/{id}")
    public BookingResponse myBooking(@AuthenticationPrincipal AuthUser farmer, @PathVariable("id") Long id) {
        return bookingService.myBooking(farmer.id(), id);
    }

    @PostMapping("/{id}/cancel")
    public BookingResponse cancel(@AuthenticationPrincipal AuthUser farmer, @PathVariable("id") Long id) {
        return bookingService.cancel(farmer.id(), id);
    }
}
