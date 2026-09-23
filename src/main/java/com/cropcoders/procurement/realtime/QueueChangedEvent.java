package com.cropcoders.procurement.realtime;

/**
 * Published whenever a centre's queue changes (check-in, call next, booking for today, ...).
 * QueueSseHub pushes the new queue to every open TV and phone after the change is saved.
 */
public record QueueChangedEvent(Long centreId) {
}
