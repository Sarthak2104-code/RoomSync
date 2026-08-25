package com.roomsync.agent.repository;

import com.roomsync.agent.entity.AgentOperation;
import com.roomsync.agent.entity.AgentOperationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AgentOperationRepository extends JpaRepository<AgentOperation, Long> {
    Optional<AgentOperation> findByOperationId(String operationId);
    Page<AgentOperation> findAllByActingUserId(Long actingUserId, Pageable pageable);
    List<AgentOperation> findAllByStatus(AgentOperationStatus status);
    Optional<AgentOperation> findByIdempotencyKey(String idempotencyKey);
    Optional<AgentOperation> findByConfirmationId(String confirmationId);
}
