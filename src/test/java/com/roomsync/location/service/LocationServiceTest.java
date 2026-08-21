package com.roomsync.location.service;

import com.roomsync.common.response.PageResponse;
import com.roomsync.location.dto.CreateLocationRequest;
import com.roomsync.location.dto.LocationResponse;
import com.roomsync.location.dto.UpdateLocationRequest;
import com.roomsync.location.entity.Location;
import com.roomsync.location.exception.DuplicateLocationCodeException;
import com.roomsync.location.exception.DuplicateLocationNameException;
import com.roomsync.location.exception.LocationAlreadyInactiveException;
import com.roomsync.location.exception.LocationNotFoundException;
import com.roomsync.location.repository.LocationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
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
class LocationServiceTest {

    @Mock
    private LocationRepository locationRepository;

    private LocationService locationService;

    @BeforeEach
    void setUp() {
        locationService = new LocationService(locationRepository);
    }

    @Test
    @DisplayName("Should create location successfully")
    void shouldCreateLocationSuccessfully() {
        CreateLocationRequest request = CreateLocationRequest.builder()
                .name("Mumbai")
                .code("MUM")
                .build();

        when(locationRepository.existsByCodeIgnoreCase("MUM")).thenReturn(false);
        when(locationRepository.existsByNameIgnoreCase("Mumbai")).thenReturn(false);
        when(locationRepository.save(any(Location.class))).thenAnswer(i -> {
            Location loc = i.getArgument(0);
            loc.setId(1L);
            return loc;
        });

        LocationResponse response = locationService.createLocation(request);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getName()).isEqualTo("Mumbai");
        assertThat(response.getCode()).isEqualTo("MUM");
        assertThat(response.isActive()).isTrue();
    }

    @Test
    @DisplayName("Should throw DuplicateLocationCodeException when code exists")
    void shouldThrowWhenDuplicateCode() {
        CreateLocationRequest request = CreateLocationRequest.builder()
                .name("Mumbai West")
                .code("MUM")
                .build();

        when(locationRepository.existsByCodeIgnoreCase("MUM")).thenReturn(true);

        assertThatThrownBy(() -> locationService.createLocation(request))
                .isInstanceOf(DuplicateLocationCodeException.class);
    }

    @Test
    @DisplayName("Should throw DuplicateLocationNameException when name exists")
    void shouldThrowWhenDuplicateName() {
        CreateLocationRequest request = CreateLocationRequest.builder()
                .name("Mumbai")
                .code("BOM")
                .build();

        when(locationRepository.existsByCodeIgnoreCase("BOM")).thenReturn(false);
        when(locationRepository.existsByNameIgnoreCase("Mumbai")).thenReturn(true);

        assertThatThrownBy(() -> locationService.createLocation(request))
                .isInstanceOf(DuplicateLocationNameException.class);
    }

    @Test
    @DisplayName("Should get location by ID")
    void shouldGetLocationById() {
        Location location = Location.builder()
                .id(1L)
                .name("Mumbai")
                .code("MUM")
                .active(true)
                .build();

        when(locationRepository.findById(1L)).thenReturn(Optional.of(location));

        LocationResponse response = locationService.getLocationById(1L);

        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getName()).isEqualTo("Mumbai");
    }

    @Test
    @DisplayName("Should throw LocationNotFoundException when location not found")
    void shouldThrowWhenLocationNotFound() {
        when(locationRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> locationService.getLocationById(99L))
                .isInstanceOf(LocationNotFoundException.class);
    }

    @Test
    @DisplayName("Should list active locations with pagination")
    void shouldListActiveLocations() {
        Location location = Location.builder().id(1L).name("Mumbai").code("MUM").active(true).build();
        Page<Location> page = new PageImpl<>(List.of(location), PageRequest.of(0, 10), 1);

        when(locationRepository.findAllByActiveTrue(any(Pageable.class))).thenReturn(page);

        PageResponse<LocationResponse> response = locationService.getLocations(PageRequest.of(0, 10));

        assertThat(response.getContent()).hasSize(1);
        assertThat(response.getContent().get(0).getName()).isEqualTo("Mumbai");
    }

    @Test
    @DisplayName("Should update location successfully")
    void shouldUpdateLocation() {
        Location location = Location.builder().id(1L).name("Mumbai").code("MUM").active(true).build();
        UpdateLocationRequest request = UpdateLocationRequest.builder().name("Greater Mumbai").code("BOM").build();

        when(locationRepository.findById(1L)).thenReturn(Optional.of(location));
        when(locationRepository.existsByCodeIgnoreCaseAndIdNot("BOM", 1L)).thenReturn(false);
        when(locationRepository.existsByNameIgnoreCaseAndIdNot("Greater Mumbai", 1L)).thenReturn(false);
        when(locationRepository.save(any(Location.class))).thenAnswer(i -> i.getArgument(0));

        LocationResponse response = locationService.updateLocation(1L, request);

        assertThat(response.getName()).isEqualTo("Greater Mumbai");
        assertThat(response.getCode()).isEqualTo("BOM");
    }

    @Test
    @DisplayName("Should soft-deactivate location")
    void shouldDeactivateLocation() {
        Location location = Location.builder().id(1L).name("Mumbai").code("MUM").active(true).build();

        when(locationRepository.findById(1L)).thenReturn(Optional.of(location));
        when(locationRepository.save(any(Location.class))).thenAnswer(i -> i.getArgument(0));

        locationService.deactivateLocation(1L);

        assertThat(location.isActive()).isFalse();
        verify(locationRepository).save(location);
    }

    @Test
    @DisplayName("Should throw LocationAlreadyInactiveException when deactivating already inactive location")
    void shouldThrowWhenAlreadyInactive() {
        Location location = Location.builder().id(1L).name("Mumbai").code("MUM").active(false).build();

        when(locationRepository.findById(1L)).thenReturn(Optional.of(location));

        assertThatThrownBy(() -> locationService.deactivateLocation(1L))
                .isInstanceOf(LocationAlreadyInactiveException.class);
    }
}
