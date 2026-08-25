package com.roomsync.agent.service;

import com.roomsync.agent.dto.OperationResponse;
import com.roomsync.agent.entity.AgentOperation;
import com.roomsync.agent.entity.AgentOperationStatus;
import com.roomsync.agent.repository.AgentOperationRepository;
import com.roomsync.common.response.PageResponse;
import com.roomsync.user.entity.Role;
import com.roomsync.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaginationAndSortingTest {

    @Mock
    private AgentOperationRepository agentOperationRepository;

    @InjectMocks
    private OperationService operationService;

    @Test
    @DisplayName("G. Pagination & Sorting: enforces deterministic secondary sorting (id DESC)")
    void testDeterministicSecondarySorting() {
        Role adminRole = Role.builder().id(2L).name("ADMIN").build();
        User admin = User.builder().id(1L).name("Admin").role(adminRole).build();

        OffsetDateTime sameTime = OffsetDateTime.now();
        AgentOperation op1 = AgentOperation.builder().id(101L).operationId("op_101").actingUser(admin).status(AgentOperationStatus.SUCCEEDED).createdAt(sameTime).build();
        AgentOperation op2 = AgentOperation.builder().id(102L).operationId("op_102").actingUser(admin).status(AgentOperationStatus.SUCCEEDED).createdAt(sameTime).build();

        when(agentOperationRepository.findAll(any(Pageable.class)))
                .thenAnswer(inv -> {
                    Pageable pageable = inv.getArgument(0);
                    assertThat(pageable.getSort().getOrderFor("id")).isNotNull();
                    return new PageImpl<>(List.of(op2, op1), pageable, 2);
                });

        PageResponse<OperationResponse> response = operationService.getOperations(admin, PageRequest.of(0, 10));

        assertThat(response.getContent()).hasSize(2);
        assertThat(response.getContent().get(0).getOperationId()).isEqualTo("op_102");
        assertThat(response.getContent().get(1).getOperationId()).isEqualTo("op_101");
    }
}
