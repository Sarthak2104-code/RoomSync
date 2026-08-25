package com.roomsync.room.service;

import com.roomsync.common.response.PageResponse;
import com.roomsync.room.dto.AmenityResponse;
import com.roomsync.room.dto.CreateAmenityRequest;
import com.roomsync.room.dto.UpdateAmenityRequest;
import com.roomsync.room.entity.Amenity;
import com.roomsync.room.exception.AmenityNotFoundException;
import com.roomsync.room.exception.DuplicateAmenityNameException;
import com.roomsync.room.repository.AmenityRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AmenityService {

    private final AmenityRepository amenityRepository;

    @Transactional
    public AmenityResponse createAmenity(CreateAmenityRequest request) {
        String normalizedName = request.getName().trim();
        if (amenityRepository.existsByNameIgnoreCase(normalizedName)) {
            throw new DuplicateAmenityNameException(normalizedName);
        }

        Amenity amenity = Amenity.builder()
                .name(normalizedName)
                .icon(request.getIcon())
                .description(request.getDescription())
                .build();

        Amenity saved = amenityRepository.save(amenity);
        log.info("Created amenity id: {} with name: '{}'", saved.getId(), saved.getName());
        return AmenityResponse.fromEntity(saved);
    }

    @Transactional(readOnly = true)
    public AmenityResponse getAmenityById(Long id) {
        Amenity amenity = amenityRepository.findById(id)
                .orElseThrow(() -> new AmenityNotFoundException(id));
        return AmenityResponse.fromEntity(amenity);
    }

    @Transactional(readOnly = true)
    public PageResponse<AmenityResponse> getAmenities(Pageable pageable) {
        Page<Amenity> page = amenityRepository.findAll(pageable);
        return PageResponse.fromPage(page, AmenityResponse::fromEntity);
    }

    @Transactional
    public AmenityResponse updateAmenity(Long id, UpdateAmenityRequest request) {
        Amenity amenity = amenityRepository.findById(id)
                .orElseThrow(() -> new AmenityNotFoundException(id));

        if (request.getName() != null && !request.getName().trim().isEmpty()) {
            String normalizedName = request.getName().trim();
            if (!normalizedName.equalsIgnoreCase(amenity.getName()) && amenityRepository.existsByNameIgnoreCase(normalizedName)) {
                throw new DuplicateAmenityNameException(normalizedName);
            }
            amenity.setName(normalizedName);
        }

        if (request.getIcon() != null) {
            amenity.setIcon(request.getIcon());
        }

        if (request.getDescription() != null) {
            amenity.setDescription(request.getDescription());
        }

        Amenity updated = amenityRepository.save(amenity);
        log.info("Updated amenity id: {}", updated.getId());
        return AmenityResponse.fromEntity(updated);
    }

    @Transactional
    public void deleteAmenity(Long id) {
        Amenity amenity = amenityRepository.findById(id)
                .orElseThrow(() -> new AmenityNotFoundException(id));
        amenityRepository.delete(amenity);
        log.info("Deleted amenity id: {}", id);
    }
}
