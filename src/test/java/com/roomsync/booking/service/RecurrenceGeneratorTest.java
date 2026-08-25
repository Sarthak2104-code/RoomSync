package com.roomsync.booking.service;

import com.roomsync.booking.entity.RecurrenceFrequency;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RecurrenceGeneratorTest {

    private RecurrenceGenerator recurrenceGenerator;

    @BeforeEach
    void setUp() {
        recurrenceGenerator = new RecurrenceGenerator();
    }

    @Test
    @DisplayName("DAILY: Generates correct dates using occurrenceCount")
    void testDailyWithOccurrenceCount() {
        LocalDate start = LocalDate.of(2026, 9, 1);
        List<RecurrenceGenerator.OccurrenceDate> dates = recurrenceGenerator.generateOccurrences(
                RecurrenceFrequency.DAILY,
                start,
                null,
                5,
                null,
                null
        );

        assertThat(dates).hasSize(5);
        assertThat(dates.get(0)).isEqualTo(new RecurrenceGenerator.OccurrenceDate(1, LocalDate.of(2026, 9, 1)));
        assertThat(dates.get(1)).isEqualTo(new RecurrenceGenerator.OccurrenceDate(2, LocalDate.of(2026, 9, 2)));
        assertThat(dates.get(2)).isEqualTo(new RecurrenceGenerator.OccurrenceDate(3, LocalDate.of(2026, 9, 3)));
        assertThat(dates.get(3)).isEqualTo(new RecurrenceGenerator.OccurrenceDate(4, LocalDate.of(2026, 9, 4)));
        assertThat(dates.get(4)).isEqualTo(new RecurrenceGenerator.OccurrenceDate(5, LocalDate.of(2026, 9, 5)));
    }

    @Test
    @DisplayName("DAILY: Generates correct dates using endDate")
    void testDailyWithEndDate() {
        LocalDate start = LocalDate.of(2026, 9, 1);
        LocalDate end = LocalDate.of(2026, 9, 4);

        List<RecurrenceGenerator.OccurrenceDate> dates = recurrenceGenerator.generateOccurrences(
                RecurrenceFrequency.DAILY,
                start,
                end,
                null,
                null,
                null
        );

        assertThat(dates).hasSize(4);
        assertThat(dates.get(0).getDate()).isEqualTo(LocalDate.of(2026, 9, 1));
        assertThat(dates.get(3).getDate()).isEqualTo(LocalDate.of(2026, 9, 4));
    }

    @Test
    @DisplayName("WEEKLY: Generates only matching days of week")
    void testWeeklyMatchingDays() {
        // 2026-09-01 is a Tuesday
        LocalDate start = LocalDate.of(2026, 9, 1);
        List<String> days = List.of("MONDAY", "WEDNESDAY", "FRIDAY");

        List<RecurrenceGenerator.OccurrenceDate> dates = recurrenceGenerator.generateOccurrences(
                RecurrenceFrequency.WEEKLY,
                start,
                null,
                4,
                days,
                null
        );

        assertThat(dates).hasSize(4);
        assertThat(dates.get(0).getDate()).isEqualTo(LocalDate.of(2026, 9, 2)); // Wednesday
        assertThat(dates.get(1).getDate()).isEqualTo(LocalDate.of(2026, 9, 4)); // Friday
        assertThat(dates.get(2).getDate()).isEqualTo(LocalDate.of(2026, 9, 7)); // Monday
        assertThat(dates.get(3).getDate()).isEqualTo(LocalDate.of(2026, 9, 9)); // Wednesday

        assertThat(dates.get(0).getOccurrenceIndex()).isEqualTo(1);
        assertThat(dates.get(1).getOccurrenceIndex()).isEqualTo(2);
        assertThat(dates.get(2).getOccurrenceIndex()).isEqualTo(3);
        assertThat(dates.get(3).getOccurrenceIndex()).isEqualTo(4);
    }

    @Test
    @DisplayName("MONTHLY: Generates correct day of month with month-end clamping (e.g. 31st)")
    void testMonthlyWithMonthEndClamping() {
        LocalDate start = LocalDate.of(2026, 1, 31);

        List<RecurrenceGenerator.OccurrenceDate> dates = recurrenceGenerator.generateOccurrences(
                RecurrenceFrequency.MONTHLY,
                start,
                null,
                4,
                null,
                31
        );

        assertThat(dates).hasSize(4);
        assertThat(dates.get(0).getDate()).isEqualTo(LocalDate.of(2026, 1, 31)); // Jan 31
        assertThat(dates.get(1).getDate()).isEqualTo(LocalDate.of(2026, 2, 28)); // Feb 28 (2026 is non-leap)
        assertThat(dates.get(2).getDate()).isEqualTo(LocalDate.of(2026, 3, 31)); // Mar 31
        assertThat(dates.get(3).getDate()).isEqualTo(LocalDate.of(2026, 4, 30)); // Apr 30
    }

    @Test
    @DisplayName("Bound validation: Rejects both endDate and occurrenceCount present")
    void testRejectsBothBoundsPresent() {
        LocalDate start = LocalDate.of(2026, 9, 1);
        LocalDate end = LocalDate.of(2026, 9, 10);

        assertThatThrownBy(() -> recurrenceGenerator.generateOccurrences(
                RecurrenceFrequency.DAILY,
                start,
                end,
                10,
                null,
                null
        )).isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Exactly one of endDate or occurrenceCount must be provided");
    }

    @Test
    @DisplayName("Bound validation: Rejects neither endDate nor occurrenceCount present")
    void testRejectsNeitherBoundPresent() {
        LocalDate start = LocalDate.of(2026, 9, 1);

        assertThatThrownBy(() -> recurrenceGenerator.generateOccurrences(
                RecurrenceFrequency.DAILY,
                start,
                null,
                null,
                null,
                null
        )).isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Exactly one of endDate or occurrenceCount must be provided");
    }

    @Test
    @DisplayName("Bound validation: Rejects endDate before startDate")
    void testRejectsEndDateBeforeStartDate() {
        LocalDate start = LocalDate.of(2026, 9, 10);
        LocalDate end = LocalDate.of(2026, 9, 1);

        assertThatThrownBy(() -> recurrenceGenerator.generateOccurrences(
                RecurrenceFrequency.DAILY,
                start,
                end,
                null,
                null,
                null
        )).isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("End date cannot be before start date");
    }
}
