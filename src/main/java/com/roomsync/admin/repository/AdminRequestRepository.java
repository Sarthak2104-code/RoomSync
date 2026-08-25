package com.roomsync.admin.repository;

import com.roomsync.admin.entity.AdminRequest;
import com.roomsync.admin.entity.AdminRequestStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AdminRequestRepository extends JpaRepository<AdminRequest, Long> {

    Page<AdminRequest> findAllByRequesterUserId(Long requesterUserId, Pageable pageable);

    List<AdminRequest> findAllByStatus(AdminRequestStatus status);

    @Query("""
        SELECT ar FROM AdminRequest ar
        WHERE (:status IS NULL OR ar.status = :status)
          AND (:locationId IS NULL OR ar.location.id = :locationId)
    """)
    Page<AdminRequest> findAllByFilters(
        @Param("status") AdminRequestStatus status,
        @Param("locationId") Long locationId,
        Pageable pageable
    );
}
