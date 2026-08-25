package com.roomsync.room.service;

import com.roomsync.common.response.PageResponse;
import com.roomsync.room.dto.AmenityResponse;
import com.roomsync.room.dto.CreateAmenityRequest;
import com.roomsync.room.dto.UpdateAmenityRequest;
import com.roomsync.room.entity.Amenity;
import com.roomsync.room.exception.AmenityNotFoundException;
import com.roomsync.room.exception.DuplicateAmenityNameException;
import com.roomsync.room.repository.AmenityRepository;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AmenityServiceTest {

    @Mock
    private AmenityRepository amenityRepository;

    @InjectMocks
    private AmenityService amenityService;

    private Amenity projector;

    @BeforeEach
    void setUp() {
        projector = Amenity.builder()
                .id(1L)
                .name("4K Projector")
                .icon("projector-icon")
                .description("High-res 4K projector")
                .build();
    }

    @Test
    @DisplayName("Create amenity successfully")
    void testCreateAmenitySuccess() {
        when(amenityRepository.existsByNameIgnoreCase("4K Projector")).thenReturn(false);
        when(amenityRepository.save(any(Amenity.class))).thenAnswer(inv -> {
            Amenity a = inv.getArgument(0);
            a.setId(1L);
            return a;
        });

        CreateAmenityRequest request = CreateAmenityRequest.builder()
                .name("4K Projector")
                .icon("projector-icon")
                .description("High-res 4K projector")
                .build();

        AmenityResponse response = amenityService.createAmenity(request);

        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getName()).isEqualTo("4K Projector");
    }

    @Test
    @DisplayName("Create amenity fails on duplicate name")
    void testCreateAmenityDuplicateName() {
        when(amenityRepository.existsByNameIgnoreCase("4K Projector")).thenReturn(true);

        CreateAmenityRequest request = CreateAmenityRequest.builder()
                .name("4K Projector")
                .build();

        assertThatThrownBy(() -> amenityService.createAmenity(request))
                .isInstanceOf(DuplicateAmenityNameException.class);
    }

    @Test
    @DisplayName("Update amenity successfully")
    void testUpdateAmenity() {
        when(amenityRepository.findById(1L)).thenReturn(Optional.of(projector));
        when(amenityRepository.save(any(Amenity.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateAmenityRequest request = UpdateAmenityRequest.builder()
                .name("Ultra 4K Projector")
                .description("Updated desc")
                .build();

        AmenityResponse response = amenityService.updateAmenity(1L, request);

        assertThat(response.getName()).isEqualTo("Ultra 4K Projector");
        assertThat(response.getDescription()).isEqualTo("Updated desc");
    }

    @Test
    @DisplayName("Delete amenity successfully")
    void testDeleteAmenity() {
        when(amenityRepository.findById(1L)).thenReturn(Optional.of(projector));

        amenityService.deleteAmenity(1L);

        verify(amenityRepository).delete(projector);
    }
}
