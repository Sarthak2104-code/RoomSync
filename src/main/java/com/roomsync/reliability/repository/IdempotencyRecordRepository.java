package com.roomsync.reliability.repository;

import com.roomsync.reliability.entity.IdempotencyRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, Long> {
    Optional<IdempotencyRecord> findByScopeUserIdAndIdempotencyKey(Long scopeUserId, String idempotencyKey);
    boolean existsByScopeUserIdAndIdempotencyKey(Long scopeUserId, String idempotencyKey);
}
