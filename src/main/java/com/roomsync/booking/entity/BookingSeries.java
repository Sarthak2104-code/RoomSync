package com.roomsync.booking.entity;

import com.roomsync.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;

/**
 * BookingSeries Entity representing recurring booking schedules (DAILY, WEEKLY, MONTHLY).
 * Bound by either end_date XOR occurrence_count.
 */
@Entity
@Table(name = "booking_series")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BookingSeries {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "series_name")
    private String seriesName;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "frequency", nullable = false, length = 50)
    private RecurrenceFrequency frequency;

    @NotNull
    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "occurrence_count")
    private Integer occurrenceCount;

    @NotNull
    @Column(name = "start_local_time", nullable = false)
    private LocalTime startLocalTime;

    @NotNull
    @Column(name = "end_local_time", nullable = false)
    private LocalTime endLocalTime;

    @NotBlank
    @Column(name = "timezone", nullable = false, length = 100)
    private String timezone;

    @Column(name = "days_of_week", length = 100)
    private String daysOfWeek;

    @Column(name = "day_of_month")
    private Integer dayOfMonth;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    private BookingSeriesStatus status;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;
}
