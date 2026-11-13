package com.cropcoders.procurement.entity;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import com.cropcoders.procurement.enums.AppIssue;
import com.cropcoders.procurement.enums.CentreIssue;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/**
 * The farmer's review of one visit, given after the crop was weighed: stars for the centre and the app,
 * what should improve, and optional comments. One per booking, never changed afterwards.
 * The improvement codes are kept comma-separated in one column, e.g. "WAITING,FACILITIES".
 */
@Entity
@Table(name = "visit_feedback")
public class VisitFeedback {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long bookingId;
    private Long farmerId;
    private Long centreId;

    // SMALLINT in the database (1 to 5)
    private short centreRating;
    private short appRating;

    private String centreIssues;
    private String appIssues;
    private String centreComment;
    private String appComment;

    private LocalDateTime createdAt;

    protected VisitFeedback() {
        // for JPA
    }

    public VisitFeedback(Booking booking, int centreRating, int appRating, Collection<CentreIssue> centreIssues,
                         Collection<AppIssue> appIssues, String centreComment, String appComment) {
        this.bookingId = booking.getId();
        this.farmerId = booking.getFarmer().getId();
        this.centreId = booking.getCentre().getId();
        this.centreRating = (short) centreRating;
        this.appRating = (short) appRating;
        this.centreIssues = joinCodes(centreIssues);
        this.appIssues = joinCodes(appIssues);
        this.centreComment = centreComment;
        this.appComment = appComment;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now().withNano(0);
        }
    }

    /** [WAITING, FACILITIES] -> "WAITING,FACILITIES"; nothing chosen -> null. */
    public static String joinCodes(Collection<? extends Enum<?>> codes) {
        if (codes == null || codes.isEmpty()) {
            return null;
        }
        return codes.stream().map(Enum::name).collect(Collectors.joining(","));
    }

    /** "WAITING,FACILITIES" -> [WAITING, FACILITIES]. A code this version does not know is left out. */
    public static <E extends Enum<E>> List<E> splitCodes(String text, Class<E> type) {
        List<E> codes = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return codes;
        }
        for (String code : text.split(",")) {
            for (E known : type.getEnumConstants()) {
                if (known.name().equals(code.trim())) {
                    codes.add(known);
                }
            }
        }
        return codes;
    }

    public Long getId() {
        return id;
    }

    public Long getBookingId() {
        return bookingId;
    }

    public Long getFarmerId() {
        return farmerId;
    }

    public Long getCentreId() {
        return centreId;
    }

    public int getCentreRating() {
        return centreRating;
    }

    public int getAppRating() {
        return appRating;
    }

    public List<CentreIssue> getCentreIssues() {
        return splitCodes(centreIssues, CentreIssue.class);
    }

    public List<AppIssue> getAppIssues() {
        return splitCodes(appIssues, AppIssue.class);
    }

    public String getCentreComment() {
        return centreComment;
    }

    public String getAppComment() {
        return appComment;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
