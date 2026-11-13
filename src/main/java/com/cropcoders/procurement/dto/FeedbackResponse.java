package com.cropcoders.procurement.dto;

import java.time.LocalDateTime;
import java.util.List;

import com.cropcoders.procurement.entity.VisitFeedback;
import com.cropcoders.procurement.enums.AppIssue;
import com.cropcoders.procurement.enums.CentreIssue;

/** The farmer's own review of a visit, shown on their booking page. A comment not written is null. */
public record FeedbackResponse(
        int centreRating,
        int appRating,
        List<CentreIssue> centreIssues,
        List<AppIssue> appIssues,
        String centreComment,
        String appComment,
        LocalDateTime createdAt) {

    public static FeedbackResponse from(VisitFeedback f) {
        return new FeedbackResponse(f.getCentreRating(), f.getAppRating(), f.getCentreIssues(), f.getAppIssues(),
                f.getCentreComment(), f.getAppComment(), f.getCreatedAt());
    }
}
