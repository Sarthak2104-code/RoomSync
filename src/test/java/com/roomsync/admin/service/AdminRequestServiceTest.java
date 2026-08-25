package com.roomsync.admin.service;

import com.roomsync.admin.dto.AdminRequestResponse;
import com.roomsync.admin.dto.ResolveAdminRequestRequest;
import com.roomsync.admin.entity.AdminRequest;
import com.roomsync.admin.entity.AdminRequestStatus;
import com.roomsync.admin.exception.AdminRequestNotFoundException;
import com.roomsync.admin.repository.AdminRequestRepository;
import com.roomsync.common.response.PageResponse;
import com.roomsync.user.entity.User;
import com.roomsync.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminRequestServiceTest {

    @Mock
    private AdminRequestRepository adminRequestRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private AdminRequestService adminRequestService;

    private User requester;
    private User admin;
    private AdminRequest adminRequest;

    @BeforeEach
    void setUp() {
        requester = User.builder()
                .id(100L)
                .name("Alice")
                .email("alice@roomsync.com")
                .build();

        admin = User.builder()
                .id(1L)
                .name("Admin User")
                .email("admin@roomsync.com")
                .build();

        adminRequest = AdminRequest.builder()
                .id(50L)
                .requesterUser(requester)
                .requestType("RECURRING_CONFLICT")
                .message("Need help with room conflict")
                .status(AdminRequestStatus.OPEN)
                .build();
    }

    @Test
    @DisplayName("Listing admin requests with filters")
    void testGetAdminRequests() {
        Pageable pageable = PageRequest.of(0, 10);
        when(adminRequestRepository.findAllByFilters(AdminRequestStatus.OPEN, 1L, pageable))
                .thenReturn(new PageImpl<>(List.of(adminRequest), pageable, 1));

        PageResponse<AdminRequestResponse> response = adminRequestService.getAdminRequests(AdminRequestStatus.OPEN, 1L, pageable);

        assertThat(response.getContent()).hasSize(1);
        assertThat(response.getContent().get(0).getId()).isEqualTo(50L);
        assertThat(response.getContent().get(0).getStatus()).isEqualTo(AdminRequestStatus.OPEN);
    }

    @Test
    @DisplayName("Retrieving admin request by ID")
    void testGetAdminRequestById() {
        when(adminRequestRepository.findById(50L)).thenReturn(Optional.of(adminRequest));

        AdminRequestResponse response = adminRequestService.getAdminRequestById(50L);

        assertThat(response.getId()).isEqualTo(50L);
        assertThat(response.getMessage()).isEqualTo("Need help with room conflict");
    }

    @Test
    @DisplayName("Resolving admin request transitions status to RESOLVED and records admin user")
    void testResolveAdminRequest() {
        when(adminRequestRepository.findById(50L)).thenReturn(Optional.of(adminRequest));
        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(adminRequestRepository.save(any(AdminRequest.class))).thenAnswer(inv -> inv.getArgument(0));

        ResolveAdminRequestRequest resolveDto = ResolveAdminRequestRequest.builder()
                .status(AdminRequestStatus.RESOLVED)
                .resolutionNotes("Assigned alternate Room Beta")
                .build();

        AdminRequestResponse response = adminRequestService.resolveAdminRequest(50L, 1L, resolveDto);

        assertThat(response.getStatus()).isEqualTo(AdminRequestStatus.RESOLVED);
        assertThat(response.getResolvedByUserId()).isEqualTo(1L);
        assertThat(response.getMessage()).contains("Resolution: Assigned alternate Room Beta");
    }
}
