package com.roomsync.booking.repository;

import com.roomsync.booking.entity.BookingOccurrenceException;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BookingOccurrenceExceptionRepository extends JpaRepository<BookingOccurrenceException, Long> {

    Optional<BookingOccurrenceException> findBySeriesIdAndOccurrenceIndex(Long seriesId, Integer occurrenceIndex);

    boolean existsBySeriesIdAndOccurrenceIndex(Long seriesId, Integer occurrenceIndex);

    List<BookingOccurrenceException> findAllBySeriesIdOrderByOccurrenceIndexAsc(Long seriesId);
}
