package com.cropcoders.procurement.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.cropcoders.procurement.dto.StaffRequest;
import com.cropcoders.procurement.dto.StaffUpdateRequest;
import com.cropcoders.procurement.dto.StaffView;
import com.cropcoders.procurement.security.AuthUser;
import com.cropcoders.procurement.service.StaffService;

import jakarta.validation.Valid;

/** Admin and staff accounts. Super admin: every centre. Centre admin: the staff of their own centre. */
@RestController
@RequestMapping("/api/admin/staff")
public class StaffController {

    private final StaffService staffService;

    public StaffController(StaffService staffService) {
        this.staffService = staffService;
    }

    @GetMapping
    public List<StaffView> list(@AuthenticationPrincipal AuthUser me) {
        return staffService.list(me);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public StaffView create(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody StaffRequest request) {
        return staffService.create(me, request);
    }

    @PutMapping("/{id}")
    public StaffView update(@AuthenticationPrincipal AuthUser me, @PathVariable("id") Long id,
                            @Valid @RequestBody StaffUpdateRequest request) {
        return staffService.update(me, id, request);
    }
}
