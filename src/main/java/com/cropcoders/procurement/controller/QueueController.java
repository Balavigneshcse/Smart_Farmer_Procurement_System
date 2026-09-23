package com.cropcoders.procurement.controller;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.cropcoders.procurement.dto.PublicQueueResponse;
import com.cropcoders.procurement.realtime.QueueSseHub;
import com.cropcoders.procurement.service.QueueService;

/** Public live queue (token numbers only) for the TV display and farmers' phones. */
@RestController
@RequestMapping("/api/queue")
public class QueueController {

    private final QueueService queueService;
    private final QueueSseHub sseHub;

    public QueueController(QueueService queueService, QueueSseHub sseHub) {
        this.queueService = queueService;
        this.sseHub = sseHub;
    }

    @GetMapping("/{centreId}")
    public PublicQueueResponse snapshot(@PathVariable("centreId") Long centreId) {
        return queueService.publicSnapshot(centreId);
    }

    @GetMapping(value = "/{centreId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@PathVariable("centreId") Long centreId) {
        return sseHub.subscribe(centreId);
    }
}
