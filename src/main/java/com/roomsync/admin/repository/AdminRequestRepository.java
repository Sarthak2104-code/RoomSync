package com.roomsync.admin.repository;

import com.roomsync.admin.entity.AdminRequest;
import com.roomsync.admin.entity.AdminRequestStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AdminRequestRepository extends JpaRepository<AdminRequest, Long> {
    Page<AdminRequest> findAllByRequesterUserId(Long requesterUserId, Pageable pageable);
    List<AdminRequest> findAllByStatus(AdminRequestStatus status);
}
