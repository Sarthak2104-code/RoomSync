package com.roomsync.agent.service;

import com.roomsync.agent.dto.OperationResponse;
import com.roomsync.agent.entity.AgentOperation;
import com.roomsync.agent.entity.AgentOperationStatus;
import com.roomsync.agent.exception.ForbiddenOperationAccessException;
import com.roomsync.agent.exception.OperationNotFoundException;
import com.roomsync.agent.repository.AgentOperationRepository;
import com.roomsync.user.entity.Role;
import com.roomsync.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OperationServiceTest {

    @Mock
    private AgentOperationRepository agentOperationRepository;

    @InjectMocks
    private OperationService operationService;

    private User userA;
    private User userB;
    private User admin;
    private AgentOperation operationA;

    @BeforeEach
    void setUp() {
        Role userRole = Role.builder().id(1L).name("USER").build();
        Role adminRole = Role.builder().id(2L).name("ADMIN").build();

        userA = User.builder().id(10L).wissenId("WT1161").name("Alice").role(userRole).build();
        userB = User.builder().id(20L).wissenId("WT1162").name("Bob").role(userRole).build();
        admin = User.builder().id(99L).wissenId("WT1163").name("Admin").role(adminRole).build();

        operationA = AgentOperation.builder()
                .id(1L)
                .operationId("op_user_a")
                .operationType("BOOKING_CREATE")
                .actingUser(userA)
                .status(AgentOperationStatus.SUCCEEDED)
                .build();
    }

    @Test
    @DisplayName("User can read their own operation")
    void testGetOwnOperation() {
        when(agentOperationRepository.findByOperationId("op_user_a")).thenReturn(Optional.of(operationA));

        OperationResponse response = operationService.getOperation("op_user_a", userA);

        assertThat(response.getOperationId()).isEqualTo("op_user_a");
        assertThat(response.getStatus()).isEqualTo(AgentOperationStatus.SUCCEEDED);
    }

    @Test
    @DisplayName("User cannot read another user's operation (403 Forbidden)")
    void testGetOtherUserOperationForbidden() {
        when(agentOperationRepository.findByOperationId("op_user_a")).thenReturn(Optional.of(operationA));

        assertThatThrownBy(() -> operationService.getOperation("op_user_a", userB))
                .isInstanceOf(ForbiddenOperationAccessException.class);
    }

    @Test
    @DisplayName("Admin can inspect any user's operation")
    void testAdminCanInspectAnyOperation() {
        when(agentOperationRepository.findByOperationId("op_user_a")).thenReturn(Optional.of(operationA));

        OperationResponse response = operationService.getOperation("op_user_a", admin);

        assertThat(response.getOperationId()).isEqualTo("op_user_a");
    }

    @Test
    @DisplayName("Unknown operation returns 404 OperationNotFoundException")
    void testUnknownOperationNotFound() {
        when(agentOperationRepository.findByOperationId("op_unknown")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> operationService.getOperation("op_unknown", userA))
                .isInstanceOf(OperationNotFoundException.class);
    }
}
