package com.roomsync.booking.service;

import com.roomsync.booking.entity.RecurrenceFrequency;
import lombok.Value;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Deterministic recurrence generator producing ordered occurrence dates for DAILY, WEEKLY, and MONTHLY schedules.
 */
@Component
public class RecurrenceGenerator {

    private static final int MAX_OCCURRENCES_LIMIT = 500;

    @Value
    public static class OccurrenceDate {
        int occurrenceIndex;
        LocalDate date;
    }

    public List<OccurrenceDate> generateOccurrences(
            RecurrenceFrequency frequency,
            LocalDate startDate,
            LocalDate endDate,
            Integer occurrenceCount,
            List<String> daysOfWeek,
            Integer dayOfMonth) {

        Objects.requireNonNull(frequency, "Recurrence frequency cannot be null");
        Objects.requireNonNull(startDate, "Start date cannot be null");

        // Validate recurrence bound XOR
        boolean hasEndDate = (endDate != null);
        boolean hasCount = (occurrenceCount != null);
        if ((hasEndDate && hasCount) || (!hasEndDate && !hasCount)) {
            throw new IllegalArgumentException("Exactly one of endDate or occurrenceCount must be provided");
        }

        if (hasEndDate && endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("End date cannot be before start date");
        }

        if (hasCount && occurrenceCount <= 0) {
            throw new IllegalArgumentException("Occurrence count must be at least 1");
        }

        if (hasCount && occurrenceCount > MAX_OCCURRENCES_LIMIT) {
            throw new IllegalArgumentException(String.format("Occurrence count cannot exceed %d", MAX_OCCURRENCES_LIMIT));
        }

        return switch (frequency) {
            case DAILY -> generateDaily(startDate, endDate, occurrenceCount);
            case WEEKLY -> generateWeekly(startDate, endDate, occurrenceCount, daysOfWeek);
            case MONTHLY -> generateMonthly(startDate, endDate, occurrenceCount, dayOfMonth);
        };
    }

    private List<OccurrenceDate> generateDaily(LocalDate startDate, LocalDate endDate, Integer occurrenceCount) {
        List<OccurrenceDate> occurrences = new ArrayList<>();
        LocalDate curr = startDate;
        int index = 1;

        while (shouldContinue(curr, endDate, index, occurrenceCount, occurrences.size())) {
            occurrences.add(new OccurrenceDate(index++, curr));
            curr = curr.plusDays(1);
        }

        return occurrences;
    }

    private List<OccurrenceDate> generateWeekly(
            LocalDate startDate,
            LocalDate endDate,
            Integer occurrenceCount,
            List<String> daysOfWeekList) {

        if (daysOfWeekList == null || daysOfWeekList.isEmpty()) {
            throw new IllegalArgumentException("daysOfWeek is required for WEEKLY recurrence");
        }

        Set<DayOfWeek> targetDays = EnumSet.noneOf(DayOfWeek.class);
        for (String dayStr : daysOfWeekList) {
            if (dayStr != null && !dayStr.trim().isEmpty()) {
                targetDays.add(DayOfWeek.valueOf(dayStr.trim().toUpperCase()));
            }
        }

        if (targetDays.isEmpty()) {
            throw new IllegalArgumentException("At least one valid DayOfWeek must be provided for WEEKLY recurrence");
        }

        List<OccurrenceDate> occurrences = new ArrayList<>();
        LocalDate curr = startDate;
        int index = 1;

        while (shouldContinue(curr, endDate, index, occurrenceCount, occurrences.size())) {
            if (targetDays.contains(curr.getDayOfWeek())) {
                occurrences.add(new OccurrenceDate(index++, curr));
                if (occurrenceCount != null && index > occurrenceCount) {
                    break;
                }
            }
            curr = curr.plusDays(1);
        }

        return occurrences;
    }

    private List<OccurrenceDate> generateMonthly(
            LocalDate startDate,
            LocalDate endDate,
            Integer occurrenceCount,
            Integer dayOfMonth) {

        int targetDay = (dayOfMonth != null) ? dayOfMonth : startDate.getDayOfMonth();
        if (targetDay < 1 || targetDay > 31) {
            throw new IllegalArgumentException("dayOfMonth must be between 1 and 31");
        }

        List<OccurrenceDate> occurrences = new ArrayList<>();
        YearMonth currMonth = YearMonth.from(startDate);
        int index = 1;

        while (true) {
            int maxDayInMonth = currMonth.lengthOfMonth();
            int actualDay = Math.min(targetDay, maxDayInMonth);
            LocalDate occDate = currMonth.atDay(actualDay);

            if (!occDate.isBefore(startDate)) {
                if (endDate != null && occDate.isAfter(endDate)) {
                    break;
                }

                occurrences.add(new OccurrenceDate(index++, occDate));

                if (occurrenceCount != null && index > occurrenceCount) {
                    break;
                }
            }

            currMonth = currMonth.plusMonths(1);
            if (endDate != null && currMonth.atDay(1).isAfter(endDate)) {
                break;
            }

            if (occurrences.size() >= MAX_OCCURRENCES_LIMIT) {
                break;
            }
        }

        return occurrences;
    }

    private boolean shouldContinue(
            LocalDate currentDate,
            LocalDate endDate,
            int nextIndex,
            Integer occurrenceCount,
            int currentSize) {

        if (currentSize >= MAX_OCCURRENCES_LIMIT) {
            return false;
        }

        if (endDate != null) {
            return !currentDate.isAfter(endDate);
        }

        if (occurrenceCount != null) {
            return nextIndex <= occurrenceCount;
        }

        return false;
    }
}
