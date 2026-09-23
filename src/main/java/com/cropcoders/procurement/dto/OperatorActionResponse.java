package com.cropcoders.procurement.dto;

/** Result of an operator action (check-in, call next, ...): what happened, and the updated desk. */
public record OperatorActionResponse(String message, OperatorBookingView booking, OperatorQueueResponse queue) {
}
