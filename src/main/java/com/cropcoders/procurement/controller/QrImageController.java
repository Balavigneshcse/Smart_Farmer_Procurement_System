package com.cropcoders.procurement.controller;

import java.time.Duration;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.cropcoders.procurement.dto.BookingResponse;
import com.cropcoders.procurement.email.QrCode;
import com.cropcoders.procurement.exception.ApiException;
import com.cropcoders.procurement.repository.BookingRepository;
import com.cropcoders.procurement.security.QrLinkSigner;

/**
 * The booking QR as a picture, for emails sent through Brevo (which cannot carry inline pictures).
 * The link is signed (QrLinkSigner), so nobody can fetch other farmers' QR codes by changing the number.
 */
@RestController
public class QrImageController {

    private final BookingRepository bookingRepository;
    private final QrLinkSigner signer;

    public QrImageController(BookingRepository bookingRepository, QrLinkSigner signer) {
        this.bookingRepository = bookingRepository;
        this.signer = signer;
    }

    @GetMapping(value = "/api/qr/{bookingId}.png", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> qr(@PathVariable("bookingId") Long bookingId, @RequestParam("sig") String signature) {
        if (!signer.isValid(bookingId, signature)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "QR_NOT_FOUND", "QR code not found.");
        }
        return bookingRepository.findById(bookingId)
                .map(booking -> ResponseEntity.ok()
                        .cacheControl(CacheControl.maxAge(Duration.ofDays(7)).cachePrivate())
                        .body(QrCode.png(BookingResponse.qrText(booking), 360)))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "QR_NOT_FOUND", "QR code not found."));
    }
}
