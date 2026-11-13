package com.cropcoders.procurement.enums;

/**
 * What the centre should improve, chosen by the farmer in the visit review (in this fixed order).
 * A code that is not listed here is refused (400), because the request cannot be read.
 */
public enum CentreIssue {
    WAITING,      // waiting time
    STAFF,        // staff behaviour
    FACILITIES,   // drinking water, shade, toilets
    WEIGHING,     // weighing and grading
    PAYMENT       // payment speed
}
