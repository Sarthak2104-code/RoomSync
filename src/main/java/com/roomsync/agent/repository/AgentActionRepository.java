package com.roomsync.agent.repository;

import com.roomsync.agent.entity.AgentAction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AgentActionRepository extends JpaRepository<AgentAction, Long> {
    List<AgentAction> findAllByOperationId(Long operationId);
    List<AgentAction> findAllByRequestId(String requestId);
    List<AgentAction> findAllByTraceId(String traceId);
}
