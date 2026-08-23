package com.roomsync.location.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.roomsync.common.exception.GlobalExceptionHandler;
import com.roomsync.common.response.PageResponse;
import com.roomsync.location.dto.CreateLocationRequest;
import com.roomsync.location.dto.LocationResponse;
import com.roomsync.location.dto.UpdateLocationRequest;
import com.roomsync.location.exception.DuplicateLocationCodeException;
import com.roomsync.location.exception.LocationNotFoundException;
import com.roomsync.location.service.LocationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static com.roomsync.security.TestSecurityUtils.adminAuth;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(LocationController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class LocationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private LocationService locationService;

    @Test
    @DisplayName("POST /api/locations - Should create location and return 201")
    void shouldCreateLocation() throws Exception {
        CreateLocationRequest request = CreateLocationRequest.builder().name("Mumbai").code("MUM").build();
        LocationResponse response = LocationResponse.builder().id(1L).name("Mumbai").code("MUM").active(true).build();

        when(locationService.createLocation(any(CreateLocationRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/locations")
                        .with(adminAuth(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("Mumbai"))
                .andExpect(jsonPath("$.code").value("MUM"));
    }

    @Test
    @DisplayName("POST /api/locations - Should return 409 on duplicate code")
    void shouldReturn409OnDuplicateCode() throws Exception {
        CreateLocationRequest request = CreateLocationRequest.builder().name("Mumbai").code("MUM").build();

        when(locationService.createLocation(any(CreateLocationRequest.class)))
                .thenThrow(new DuplicateLocationCodeException("MUM"));

        mockMvc.perform(post("/api/locations")
                        .with(adminAuth(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("CONFLICT"));
    }

    @Test
    @DisplayName("GET /api/locations/{id} - Should return location")
    void shouldGetLocationById() throws Exception {
        LocationResponse response = LocationResponse.builder().id(1L).name("Mumbai").code("MUM").active(true).build();

        when(locationService.getLocationById(1L)).thenReturn(response);

        mockMvc.perform(get("/api/locations/1")
                        .with(adminAuth(1L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.code").value("MUM"));
    }

    @Test
    @DisplayName("GET /api/locations/{id} - Should return 404 when not found")
    void shouldReturn404WhenLocationNotFound() throws Exception {
        when(locationService.getLocationById(99L)).thenThrow(new LocationNotFoundException(99L));

        mockMvc.perform(get("/api/locations/99")
                        .with(adminAuth(1L)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    @DisplayName("GET /api/locations - Should return paginated locations")
    void shouldListLocations() throws Exception {
        LocationResponse response = LocationResponse.builder().id(1L).name("Mumbai").code("MUM").active(true).build();
        PageResponse<LocationResponse> pageResponse = PageResponse.<LocationResponse>builder()
                .content(List.of(response))
                .page(0)
                .size(10)
                .totalElements(1)
                .totalPages(1)
                .first(true)
                .last(true)
                .build();

        when(locationService.getLocations(any(Pageable.class))).thenReturn(pageResponse);

        mockMvc.perform(get("/api/locations")
                        .with(adminAuth(1L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].name").value("Mumbai"));
    }

    @Test
    @DisplayName("PUT /api/locations/{id} - Should update location")
    void shouldUpdateLocation() throws Exception {
        UpdateLocationRequest request = UpdateLocationRequest.builder().name("Greater Mumbai").code("BOM").build();
        LocationResponse response = LocationResponse.builder().id(1L).name("Greater Mumbai").code("BOM").active(true).build();

        when(locationService.updateLocation(eq(1L), any(UpdateLocationRequest.class))).thenReturn(response);

        mockMvc.perform(put("/api/locations/1")
                        .with(adminAuth(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Greater Mumbai"));
    }

    @Test
    @DisplayName("DELETE /api/locations/{id} - Should deactivate location and return 204")
    void shouldDeactivateLocation() throws Exception {
        doNothing().when(locationService).deactivateLocation(1L);

        mockMvc.perform(delete("/api/locations/1")
                        .with(adminAuth(1L)))
                .andExpect(status().isNoContent());
    }
}
