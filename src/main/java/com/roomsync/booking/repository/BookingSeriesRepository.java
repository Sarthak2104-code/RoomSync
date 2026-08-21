package com.roomsync.booking.repository;

import com.roomsync.booking.entity.BookingSeries;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BookingSeriesRepository extends JpaRepository<BookingSeries, Long> {
    Page<BookingSeries> findAllByUserId(Long userId, Pageable pageable);
    List<BookingSeries> findAllByUserId(Long userId);
}
