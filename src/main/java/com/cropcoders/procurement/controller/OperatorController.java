package com.cropcoders.procurement.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.cropcoders.procurement.dto.CallNextRequest;
import com.cropcoders.procurement.dto.CheckInRequest;
import com.cropcoders.procurement.dto.OperatorActionResponse;
import com.cropcoders.procurement.dto.OperatorQueueResponse;
import com.cropcoders.procurement.security.AuthUser;
import com.cropcoders.procurement.service.QueueService;

import jakarta.validation.Valid;

/** The operator desk at a centre. Only role OPERATOR (see SecurityConfig); always the operator's own centre. */
@RestController
@RequestMapping("/api/operator")
public class OperatorController {

    private final QueueService queueService;

    public OperatorController(QueueService queueService) {
        this.queueService = queueService;
    }

    @GetMapping("/queue")
    public OperatorQueueResponse queue(@AuthenticationPrincipal AuthUser operator) {
        return queueService.operatorView(operator);
    }

    @PostMapping("/checkin")
    public OperatorActionResponse checkIn(@AuthenticationPrincipal AuthUser operator,
                                          @Valid @RequestBody CheckInRequest request) {
        return queueService.checkIn(operator, request.code());
    }

    @PostMapping("/call-next")
    public OperatorActionResponse callNext(@AuthenticationPrincipal AuthUser operator,
                                           @Valid @RequestBody CallNextRequest request) {
        return queueService.callNext(operator, request.counterNo());
    }

    @PostMapping("/bookings/{id}/recall")
    public OperatorActionResponse recall(@AuthenticationPrincipal AuthUser operator, @PathVariable("id") Long id) {
        return queueService.recall(operator, id);
    }

    @PostMapping("/bookings/{id}/skip")
    public OperatorActionResponse skip(@AuthenticationPrincipal AuthUser operator, @PathVariable("id") Long id) {
        return queueService.skip(operator, id);
    }
}
