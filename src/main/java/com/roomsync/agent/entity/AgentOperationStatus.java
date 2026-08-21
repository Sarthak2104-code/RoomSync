package com.roomsync.agent.entity;

public enum AgentOperationStatus {
    REQUESTED,
    PLANNING,
    NEEDS_CLARIFICATION,
    VALIDATED,
    AWAITING_CONFIRMATION,
    CONFIRMED,
    EXECUTING,
    SUCCEEDED,
    FAILED,
    CONFLICT,
    PARTIAL_SUCCESS,
    TIMEOUT_UNKNOWN,
    CANCELLED
}
