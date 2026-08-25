package com.roomsync.location.service;

import com.roomsync.common.response.PageResponse;
import com.roomsync.location.dto.CreateLocationRequest;
import com.roomsync.location.dto.LocationResponse;
import com.roomsync.location.dto.UpdateLocationRequest;
import com.roomsync.location.entity.Location;
import com.roomsync.location.exception.DuplicateLocationCodeException;
import com.roomsync.location.exception.DuplicateLocationNameException;
import com.roomsync.location.exception.LocationAlreadyActiveException;
import com.roomsync.location.exception.LocationAlreadyInactiveException;
import com.roomsync.location.exception.LocationNotFoundException;
import com.roomsync.location.repository.LocationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class LocationService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of(
            "id", "name", "code", "active", "createdAt", "updatedAt"
    );

    private final LocationRepository locationRepository;

    @Transactional
    public LocationResponse createLocation(CreateLocationRequest request) {
        String normalizedCode = request.getCode().trim().toUpperCase();
        String normalizedName = request.getName().trim();

        if (locationRepository.existsByCodeIgnoreCase(normalizedCode)) {
            throw new DuplicateLocationCodeException(normalizedCode);
        }

        if (locationRepository.existsByNameIgnoreCase(normalizedName)) {
            throw new DuplicateLocationNameException(normalizedName);
        }

        Location location = Location.builder()
                .name(normalizedName)
                .code(normalizedCode)
                .active(true)
                .build();

        Location saved = locationRepository.save(location);
        log.info("Created location id: {} with code: '{}'", saved.getId(), saved.getCode());
        return LocationResponse.fromEntity(saved);
    }

    @Transactional(readOnly = true)
    public LocationResponse getLocationById(Long id) {
        Location location = locationRepository.findById(id)
                .orElseThrow(() -> new LocationNotFoundException(id));
        return LocationResponse.fromEntity(location);
    }

    @Transactional(readOnly = true)
    public PageResponse<LocationResponse> getLocations(Pageable pageable) {
        Pageable validatedPageable = sanitizePageable(pageable);
        Page<Location> page = locationRepository.findAllByActiveTrue(validatedPageable);
        return PageResponse.fromPage(page, LocationResponse::fromEntity);
    }

    @Transactional
    public LocationResponse updateLocation(Long id, UpdateLocationRequest request) {
        Location location = locationRepository.findById(id)
                .orElseThrow(() -> new LocationNotFoundException(id));

        String normalizedCode = request.getCode().trim().toUpperCase();
        String normalizedName = request.getName().trim();

        if (locationRepository.existsByCodeIgnoreCaseAndIdNot(normalizedCode, id)) {
            throw new DuplicateLocationCodeException(normalizedCode);
        }

        if (locationRepository.existsByNameIgnoreCaseAndIdNot(normalizedName, id)) {
            throw new DuplicateLocationNameException(normalizedName);
        }

        location.setName(normalizedName);
        location.setCode(normalizedCode);

        Location updated = locationRepository.save(location);
        log.info("Updated location id: {} with code: '{}'", updated.getId(), updated.getCode());
        return LocationResponse.fromEntity(updated);
    }

    @Transactional
    public LocationResponse activateLocation(Long id) {
        Location location = locationRepository.findById(id)
                .orElseThrow(() -> new LocationNotFoundException(id));

        if (location.isActive()) {
            throw new LocationAlreadyActiveException(id);
        }

        location.setActive(true);
        Location updated = locationRepository.save(location);
        log.info("Activated location id: {}", id);
        return LocationResponse.fromEntity(updated);
    }

    @Transactional
    public void deactivateLocation(Long id) {
        Location location = locationRepository.findById(id)
                .orElseThrow(() -> new LocationNotFoundException(id));

        if (!location.isActive()) {
            throw new LocationAlreadyInactiveException(id);
        }

        location.setActive(false);
        locationRepository.save(location);
        log.info("Soft-deactivated location id: {}", id);
    }

    private Pageable sanitizePageable(Pageable pageable) {
        int page = Math.max(pageable.getPageNumber(), 0);
        int size = Math.min(Math.max(pageable.getPageSize(), 1), MAX_PAGE_SIZE);

        Sort sort = pageable.getSort();
        if (sort.isUnsorted()) {
            sort = Sort.by(Sort.Direction.ASC, "name");
        } else {
            for (Sort.Order order : sort) {
                if (!ALLOWED_SORT_FIELDS.contains(order.getProperty())) {
                    throw new IllegalArgumentException(
                            String.format("Invalid sort property '%s'. Allowed properties: %s",
                                    order.getProperty(), ALLOWED_SORT_FIELDS)
                    );
                }
            }
        }

        return PageRequest.of(page, size, sort);
    }
}
