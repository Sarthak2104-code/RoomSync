package com.roomsync.booking.repository;

import com.roomsync.booking.entity.Booking;
import com.roomsync.booking.entity.BookingStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface BookingRepository extends JpaRepository<Booking, Long> {

    Page<Booking> findAllByUserId(Long userId, Pageable pageable);

    @Query("""
        SELECT COUNT(b) > 0
        FROM Booking b
        WHERE b.room.id = :roomId
          AND b.status = com.roomsync.booking.entity.BookingStatus.CONFIRMED
          AND b.startTime < :endTime
          AND b.endTime > :startTime
    """)
    boolean existsOverlappingBooking(
        @Param("roomId") Long roomId,
        @Param("startTime") OffsetDateTime startTime,
        @Param("endTime") OffsetDateTime endTime
    );

    @Query("""
        SELECT COUNT(b) > 0
        FROM Booking b
        WHERE b.room.id = :roomId
          AND b.id <> :bookingId
          AND b.status = com.roomsync.booking.entity.BookingStatus.CONFIRMED
          AND b.startTime < :endTime
          AND b.endTime > :startTime
    """)
    boolean existsOverlappingBookingExcludingBooking(
        @Param("roomId") Long roomId,
        @Param("bookingId") Long bookingId,
        @Param("startTime") OffsetDateTime startTime,
        @Param("endTime") OffsetDateTime endTime
    );

    @Modifying(clearAutomatically = true)
    @Query("""
        UPDATE Booking b
        SET b.status = com.roomsync.booking.entity.BookingStatus.COMPLETED,
            b.updatedAt = :now
        WHERE b.status = com.roomsync.booking.entity.BookingStatus.CONFIRMED
          AND b.endTime < :now
    """)
    int completePastConfirmedBookings(@Param("now") OffsetDateTime now);

    @Query("""
        SELECT b FROM Booking b
        WHERE b.series.id = :seriesId
          AND b.occurrenceIndex = :occurrenceIndex
        ORDER BY CASE WHEN b.status = com.roomsync.booking.entity.BookingStatus.CONFIRMED THEN 0 ELSE 1 END, b.id DESC
    """)
    List<Booking> findAllBySeriesIdAndOccurrenceIndexOrderActiveFirst(
        @Param("seriesId") Long seriesId,
        @Param("occurrenceIndex") Integer occurrenceIndex
    );

    default Optional<Booking> findBySeriesIdAndOccurrenceIndex(Long seriesId, Integer occurrenceIndex) {
        List<Booking> list = findAllBySeriesIdAndOccurrenceIndexOrderActiveFirst(seriesId, occurrenceIndex);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    List<Booking> findAllBySeriesIdOrderByOccurrenceIndexAsc(Long seriesId);

    @Query("""
        SELECT b.id FROM Booking b
        WHERE b.status = com.roomsync.booking.entity.BookingStatus.CONFIRMED
          AND b.endTime < :now
        ORDER BY b.endTime ASC
    """)
    List<Long> findPastConfirmedBookingIds(@Param("now") OffsetDateTime now);


    @Query("""
        SELECT b FROM Booking b
        WHERE b.room.id = :roomId
          AND b.status = com.roomsync.booking.entity.BookingStatus.CONFIRMED
          AND b.startTime <= :now
          AND b.endTime > :now
    """)
    Optional<Booking> findCurrentActiveBookingForRoom(
        @Param("roomId") Long roomId,
        @Param("now") OffsetDateTime now
    );

    @Query("""
        SELECT b FROM Booking b
        WHERE b.status = com.roomsync.booking.entity.BookingStatus.CONFIRMED
          AND b.startTime <= :now
          AND b.endTime > :now
    """)
    List<Booking> findAllActiveConfirmedBookingsAt(@Param("now") OffsetDateTime now);

    @Query("""
        SELECT b FROM Booking b
        WHERE b.room.id = :roomId
          AND (b.status = com.roomsync.booking.entity.BookingStatus.CONFIRMED OR b.status = com.roomsync.booking.entity.BookingStatus.COMPLETED)
          AND b.startTime < :endUtc
          AND b.endTime > :startUtc
        ORDER BY b.startTime ASC
    """)
    List<Booking> findEffectiveBookingsForRoomInInterval(
        @Param("roomId") Long roomId,
        @Param("startUtc") OffsetDateTime startUtc,
        @Param("endUtc") OffsetDateTime endUtc
    );

    @Query(value = """
        SELECT b FROM Booking b
        JOIN FETCH b.user u
        JOIN FETCH b.room r
        WHERE (:locationId IS NULL OR b.room.location.id = :locationId)
          AND (:roomId IS NULL OR b.room.id = :roomId)
          AND (:userId IS NULL OR b.user.id = :userId)
          AND (:status IS NULL OR b.status = :status)
    """, countQuery = """
        SELECT COUNT(b) FROM Booking b
        WHERE (:locationId IS NULL OR b.room.location.id = :locationId)
          AND (:roomId IS NULL OR b.room.id = :roomId)
          AND (:userId IS NULL OR b.user.id = :userId)
          AND (:status IS NULL OR b.status = :status)
    """)
    Page<Booking> findAllAdminBookings(
        @Param("locationId") Long locationId,
        @Param("roomId") Long roomId,
        @Param("userId") Long userId,
        @Param("status") BookingStatus status,
        Pageable pageable
    );
}
