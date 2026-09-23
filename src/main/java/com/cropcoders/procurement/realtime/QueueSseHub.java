package com.cropcoders.procurement.realtime;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.cropcoders.procurement.dto.PublicQueueResponse;
import com.cropcoders.procurement.service.QueueService;

/**
 * Live queue updates with Server-Sent Events (SSE).
 *
 * Every open TV display, farmer phone and operator desk keeps one HTTP connection open
 * (browser: new EventSource('/api/queue/1/stream')). When the queue of a centre changes,
 * the new queue is pushed down all connections of that centre at once. The browser
 * reconnects by itself if the connection drops.
 */
@Component
public class QueueSseHub {

    private static final Logger log = LoggerFactory.getLogger(QueueSseHub.class);
    private static final long CONNECTION_TIMEOUT_MS = Duration.ofMinutes(30).toMillis();

    private final Map<Long, List<SseEmitter>> connectionsByCentre = new ConcurrentHashMap<>();
    private final QueueService queueService;

    public QueueSseHub(QueueService queueService) {
        this.queueService = queueService;
    }

    /** A screen connects: send the current queue straight away, then updates as they happen. */
    public SseEmitter subscribe(Long centreId) {
        PublicQueueResponse snapshot = queueService.publicSnapshot(centreId);   // 404 if the centre doesn't exist

        SseEmitter emitter = new SseEmitter(CONNECTION_TIMEOUT_MS);
        List<SseEmitter> connections = connectionsByCentre.computeIfAbsent(centreId, id -> new CopyOnWriteArrayList<>());
        connections.add(emitter);
        emitter.onCompletion(() -> connections.remove(emitter));
        emitter.onTimeout(() -> connections.remove(emitter));
        emitter.onError(error -> connections.remove(emitter));

        send(emitter, connections, snapshot);
        return emitter;
    }

    /**
     * Runs after the transaction that changed the queue has committed, so screens never
     * see half-saved data. REQUIRES_NEW: read the queue in a fresh transaction.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public void onQueueChanged(QueueChangedEvent event) {
        List<SseEmitter> connections = connectionsByCentre.get(event.centreId());
        if (connections == null || connections.isEmpty()) {
            return;
        }
        PublicQueueResponse snapshot = queueService.publicSnapshot(event.centreId());
        connections.forEach(emitter -> send(emitter, connections, snapshot));
    }

    /** A small comment every 20 seconds keeps idle connections from being closed by proxies. */
    @Scheduled(fixedRate = 20_000)
    public void heartbeat() {
        connectionsByCentre.values().forEach(connections -> connections.forEach(emitter -> {
            try {
                emitter.send(SseEmitter.event().comment("ping"));
            } catch (IOException | IllegalStateException e) {
                connections.remove(emitter);
            }
        }));
    }

    public int openConnections() {
        return connectionsByCentre.values().stream().mapToInt(List::size).sum();
    }

    private void send(SseEmitter emitter, List<SseEmitter> connections, PublicQueueResponse snapshot) {
        try {
            emitter.send(SseEmitter.event().name("queue").data(snapshot, MediaType.APPLICATION_JSON));
        } catch (IOException | IllegalStateException e) {
            log.debug("Dropping closed queue connection: {}", e.getMessage());
            connections.remove(emitter);
        }
    }
}
