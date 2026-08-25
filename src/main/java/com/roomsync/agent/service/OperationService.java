package com.roomsync.agent.service;

import com.roomsync.agent.dto.OperationResponse;
import com.roomsync.agent.entity.AgentOperation;
import com.roomsync.agent.entity.AgentOperationStatus;
import com.roomsync.agent.exception.ForbiddenOperationAccessException;
import com.roomsync.agent.exception.OperationNotFoundException;
import com.roomsync.agent.repository.AgentOperationRepository;
import com.roomsync.common.response.PageResponse;
import com.roomsync.user.entity.User;
import com.roomsync.user.entity.UserRole;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class OperationService {

    private final AgentOperationRepository agentOperationRepository;

    @Transactional(readOnly = true)
    public OperationResponse getOperation(String operationId, User currentUser) {
        AgentOperation operation = agentOperationRepository.findByOperationId(operationId)
                .orElseThrow(() -> new OperationNotFoundException(operationId));

        if (currentUser.getRoleEnum() != UserRole.ADMIN && !operation.getActingUser().getId().equals(currentUser.getId())) {
            throw new ForbiddenOperationAccessException("User is not authorized to access this operation");
        }

        return OperationResponse.fromEntity(operation);
    }

    @Transactional(readOnly = true)
    public PageResponse<OperationResponse> getOperations(User currentUser, Pageable pageable) {
        Pageable sortedPageable = ensureDeterministicSort(pageable);
        Page<AgentOperation> page;
        if (currentUser.getRoleEnum() == UserRole.ADMIN) {
            page = agentOperationRepository.findAll(sortedPageable);
        } else {
            page = agentOperationRepository.findAllByActingUserId(currentUser.getId(), sortedPageable);
        }
        return PageResponse.fromPage(page, OperationResponse::fromEntity);
    }

    private Pageable ensureDeterministicSort(Pageable pageable) {
        Sort sort = pageable.getSort();
        if (sort.isUnsorted()) {
            sort = Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id"));
        } else if (sort.getOrderFor("id") == null) {
            sort = sort.and(Sort.by(Sort.Direction.DESC, "id"));
        }
        return PageRequest.of(pageable.getPageNumber(), Math.min(pageable.getPageSize(), 100), sort);
    }
}
