package com.roomsync.room.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.roomsync.common.exception.GlobalExceptionHandler;
import com.roomsync.common.response.PageResponse;
import com.roomsync.location.dto.LocationResponse;
import com.roomsync.room.dto.CreateRoomRequest;
import com.roomsync.room.dto.RoomResponse;
import com.roomsync.room.dto.UpdateRoomRequest;
import com.roomsync.room.entity.RoomStatus;
import com.roomsync.room.exception.DuplicateRoomNameException;
import com.roomsync.room.exception.RoomAlreadyLockedException;
import com.roomsync.room.exception.RoomAlreadyUnlockedException;
import com.roomsync.room.exception.RoomNotFoundException;
import com.roomsync.room.service.RoomService;
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

import java.time.OffsetDateTime;
import java.util.List;

import static com.roomsync.security.TestSecurityUtils.adminAuth;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RoomController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class RoomControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private RoomService roomService;

    @Test
    @DisplayName("POST /api/rooms - Should return 201 when payload is valid")
    void shouldCreateRoom() throws Exception {
        CreateRoomRequest request = CreateRoomRequest.builder()
                .locationId(1L)
                .name("Taj Mahal")
                .capacity(10)
                .description("Executive conference room")
                .build();

        LocationResponse locResponse = LocationResponse.builder().id(1L).name("Mumbai").code("MUM").active(true).build();

        RoomResponse response = RoomResponse.builder()
                .id(1L)
                .name("Taj Mahal")
                .capacity(10)
                .location(locResponse)
                .description("Executive conference room")
                .status(RoomStatus.AVAILABLE)
                .active(true)
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build();

        when(roomService.createRoom(eq(123L), any(CreateRoomRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/rooms")
                        .with(adminAuth(123L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("Taj Mahal"))
                .andExpect(jsonPath("$.capacity").value(10))
                .andExpect(jsonPath("$.location.code").value("MUM"))
                .andExpect(jsonPath("$.status").value("AVAILABLE"))
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    @DisplayName("POST /api/rooms - Should return 400 when capacity is zero or negative")
    void shouldRejectInvalidCapacity() throws Exception {
        CreateRoomRequest request = CreateRoomRequest.builder()
                .locationId(1L)
                .name("Taj Mahal")
                .capacity(0)
                .build();

        mockMvc.perform(post("/api/rooms")
                        .with(adminAuth(123L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.validationErrors.capacity").exists());
    }

    @Test
    @DisplayName("POST /api/rooms - Should return 400 when name is blank")
    void shouldRejectBlankName() throws Exception {
        CreateRoomRequest request = CreateRoomRequest.builder()
                .locationId(1L)
                .name("   ")
                .capacity(10)
                .build();

        mockMvc.perform(post("/api/rooms")
                        .with(adminAuth(123L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.name").exists());
    }

    @Test
    @DisplayName("POST /api/rooms - Should return 409 when room name is duplicated in same location")
    void shouldReturnConflictOnDuplicateName() throws Exception {
        CreateRoomRequest request = CreateRoomRequest.builder()
                .locationId(1L)
                .name("Taj Mahal")
                .capacity(10)
                .build();

        when(roomService.createRoom(eq(123L), any(CreateRoomRequest.class)))
                .thenThrow(new DuplicateRoomNameException("Taj Mahal", "Mumbai"));

        mockMvc.perform(post("/api/rooms")
                        .with(adminAuth(123L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("CONFLICT"))
                .andExpect(jsonPath("$.message").value("A room with the name 'Taj Mahal' already exists in location 'Mumbai'"));
    }

    @Test
    @DisplayName("GET /api/rooms - Should return 200 with paginated rooms")
    void shouldReturnPaginatedRooms() throws Exception {
        RoomResponse room = RoomResponse.builder()
                .id(1L)
                .name("Taj Mahal")
                .capacity(10)
                .status(RoomStatus.AVAILABLE)
                .active(true)
                .build();

        PageResponse<RoomResponse> pageResponse = PageResponse.<RoomResponse>builder()
                .content(List.of(room))
                .page(0)
                .size(10)
                .totalElements(1)
                .totalPages(1)
                .first(true)
                .last(true)
                .build();

        when(roomService.getRooms(eq(123L), any(), any(Pageable.class))).thenReturn(pageResponse);

        mockMvc.perform(get("/api/rooms?page=0&size=10&sort=name,asc")
                        .with(adminAuth(123L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].name").value("Taj Mahal"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.page").value(0));
    }

    @Test
    @DisplayName("GET /api/rooms/{id} - Should return 200 with room data")
    void shouldGetRoomById() throws Exception {
        RoomResponse response = RoomResponse.builder()
                .id(1L)
                .name("Taj Mahal")
                .capacity(10)
                .status(RoomStatus.AVAILABLE)
                .active(true)
                .build();

        when(roomService.getRoomById(123L, 1L)).thenReturn(response);

        mockMvc.perform(get("/api/rooms/1")
                        .with(adminAuth(123L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("Taj Mahal"));
    }

    @Test
    @DisplayName("GET /api/rooms/{id} - Should return 404 when room is missing")
    void shouldReturn404ForMissingRoom() throws Exception {
        when(roomService.getRoomById(123L, 999L)).thenThrow(new RoomNotFoundException(999L));

        mockMvc.perform(get("/api/rooms/999")
                        .with(adminAuth(123L)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("NOT_FOUND"));
    }

    @Test
    @DisplayName("PUT /api/rooms/{id} - Should update and return 200")
    void shouldUpdateRoom() throws Exception {
        UpdateRoomRequest request = UpdateRoomRequest.builder()
                .name("Taj Mahal Renamed")
                .capacity(15)
                .build();

        RoomResponse response = RoomResponse.builder()
                .id(1L)
                .name("Taj Mahal Renamed")
                .capacity(15)
                .status(RoomStatus.AVAILABLE)
                .active(true)
                .build();

        when(roomService.updateRoom(eq(123L), eq(1L), any(UpdateRoomRequest.class))).thenReturn(response);

        mockMvc.perform(put("/api/rooms/1")
                        .with(adminAuth(123L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Taj Mahal Renamed"))
                .andExpect(jsonPath("$.capacity").value(15));
    }

    @Test
    @DisplayName("PATCH /api/rooms/{id}/lock - Should lock room and return 200")
    void shouldLockRoom() throws Exception {
        RoomResponse response = RoomResponse.builder()
                .id(1L)
                .name("Taj Mahal")
                .status(RoomStatus.LOCKED)
                .active(true)
                .build();

        when(roomService.lockRoom(123L, 1L)).thenReturn(response);

        mockMvc.perform(patch("/api/rooms/1/lock")
                        .with(adminAuth(123L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("LOCKED"));
    }

    @Test
    @DisplayName("PATCH /api/rooms/{id}/lock - Should return 409 when room is already locked")
    void shouldReturnConflictWhenAlreadyLocked() throws Exception {
        when(roomService.lockRoom(123L, 1L)).thenThrow(new RoomAlreadyLockedException(1L));

        mockMvc.perform(patch("/api/rooms/1/lock")
                        .with(adminAuth(123L)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value("Room with id '1' is already LOCKED"));
    }

    @Test
    @DisplayName("PATCH /api/rooms/{id}/unlock - Should unlock room and return 200")
    void shouldUnlockRoom() throws Exception {
        RoomResponse response = RoomResponse.builder()
                .id(1L)
                .name("Taj Mahal")
                .status(RoomStatus.AVAILABLE)
                .active(true)
                .build();

        when(roomService.unlockRoom(123L, 1L)).thenReturn(response);

        mockMvc.perform(patch("/api/rooms/1/unlock")
                        .with(adminAuth(123L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AVAILABLE"));
    }

    @Test
    @DisplayName("DELETE /api/rooms/{id} - Should return 204 No Content")
    void shouldDeactivateRoom() throws Exception {
        doNothing().when(roomService).deactivateRoom(123L, 1L);

        mockMvc.perform(delete("/api/rooms/1")
                        .with(adminAuth(123L)))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("GET /api/rooms/1 - Should return 500 with sanitized message on unexpected error")
    void shouldReturnSanitized500OnUnexpectedError() throws Exception {
        when(roomService.getRoomById(123L, 1L)).thenThrow(new RuntimeException("Sensitive database connection leak info"));

        mockMvc.perform(get("/api/rooms/1")
                        .with(adminAuth(123L)))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.error").value("INTERNAL_SERVER_ERROR"))
                .andExpect(jsonPath("$.message").value("An unexpected error occurred"));
    }
}
