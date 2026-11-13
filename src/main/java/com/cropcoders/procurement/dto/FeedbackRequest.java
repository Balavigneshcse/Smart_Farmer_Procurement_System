package com.cropcoders.procurement.dto;

import java.util.EnumSet;
import java.util.List;
import java.util.Objects;

import com.cropcoders.procurement.enums.AppIssue;
import com.cropcoders.procurement.enums.CentreIssue;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * The farmer's review of a visit: 1 to 5 stars for the centre and for the app, what should improve
 * (codes from CentreIssue / AppIssue; an unknown code makes the request unreadable = 400) and optional comments.
 *
 * The compact constructor below tidies the values before they are checked: each code once and in the fixed
 * order, comments trimmed, line breaks turned into spaces, other control characters removed, an empty comment
 * becomes null. Tamil and Hindi letters (and their vowel signs) are kept as they are.
 */
public record FeedbackRequest(
        @NotNull(message = "Choose 1 to 5 stars for the centre")
        @Min(value = 1, message = "Choose 1 to 5 stars for the centre")
        @Max(value = 5, message = "Choose 1 to 5 stars for the centre")
        Integer centreRating,

        @NotNull(message = "Choose 1 to 5 stars for the app")
        @Min(value = 1, message = "Choose 1 to 5 stars for the app")
        @Max(value = 5, message = "Choose 1 to 5 stars for the app")
        Integer appRating,

        List<CentreIssue> centreIssues,
        List<AppIssue> appIssues,

        @Size(max = 500, message = "Write at most 500 characters") String centreComment,
        @Size(max = 500, message = "Write at most 500 characters") String appComment) {

    public FeedbackRequest {
        centreIssues = eachOnce(centreIssues, CentreIssue.class);
        appIssues = eachOnce(appIssues, AppIssue.class);
        centreComment = clean(centreComment);
        appComment = clean(appComment);
    }

    /** Each code once, in the order of the enum (an EnumSet keeps that order); missing list = no codes. */
    private static <E extends Enum<E>> List<E> eachOnce(List<E> codes, Class<E> type) {
        EnumSet<E> set = EnumSet.noneOf(type);
        if (codes != null) {
            codes.stream().filter(Objects::nonNull).forEach(set::add);
        }
        return List.copyOf(set);
    }

    /** "  Need shade\nnear the counters " -> "Need shade near the counters"; blank -> null. */
    static String clean(String text) {
        if (text == null) {
            return null;
        }
        String cleaned = text
                .replaceAll("[\\t\\n\\r\\f\\u000B\\u2028\\u2029]", " ")      // line breaks and tabs: a space
                .replaceAll("[\\p{Cc}\\u202A-\\u202E\\u2066-\\u2069]", "")  // other control characters, text-direction tricks
                .strip();
        return cleaned.isEmpty() ? null : cleaned;
    }
}
