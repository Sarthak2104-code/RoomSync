package com.roomsync.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.roomsync.booking.controller.BookingController;
import com.roomsync.booking.dto.BookingResponse;
import com.roomsync.booking.dto.CreateBookingRequest;
import com.roomsync.booking.entity.BookingStatus;
import com.roomsync.booking.exception.BookingNotFoundException;
import com.roomsync.booking.service.BookingService;
import com.roomsync.common.constants.CommonConstants;
import com.roomsync.common.exception.GlobalExceptionHandler;
import com.roomsync.common.filter.CorrelationIdFilter;
import com.roomsync.location.controller.LocationController;
import com.roomsync.location.dto.CreateLocationRequest;
import com.roomsync.location.dto.LocationResponse;
import com.roomsync.location.service.LocationService;
import com.roomsync.room.controller.RoomController;
import com.roomsync.room.dto.CreateRoomRequest;
import com.roomsync.room.dto.RoomResponse;
import com.roomsync.room.entity.RoomStatus;
import com.roomsync.room.service.RoomService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;

import static com.roomsync.security.TestSecurityUtils.adminAuth;
import static com.roomsync.security.TestSecurityUtils.userAuth;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({BookingController.class, RoomController.class, LocationController.class})
@Import({GlobalExceptionHandler.class, CorrelationIdFilter.class})
class ApiContractRegressionTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private BookingService bookingService;

    @MockitoBean
    private RoomService roomService;

    @MockitoBean
    private LocationService locationService;

    @Test
    @DisplayName("API Contract: Successful response returns direct DTO and includes X-Correlation-Id header")
    void testSuccessfulDirectDtoContract() throws Exception {
        BookingResponse booking = BookingResponse.builder()
                .id(100L)
                .roomId(1L)
                .roomName("Taj Mahal")
                .userId(10L)
                .startTime(OffsetDateTime.parse("2026-08-20T10:00:00Z"))
                .endTime(OffsetDateTime.parse("2026-08-20T11:00:00Z"))
                .reason("Important Meeting")
                .status(BookingStatus.CONFIRMED)
                .build();

        when(bookingService.getBooking(100L, 10L)).thenReturn(booking);

        mockMvc.perform(get("/api/bookings/100")
                        .with(userAuth(10L))
                        .header("X-Correlation-Id", "test-corr-abc"))
                .andExpect(status().isOk())
                .andExpect(header().string(CommonConstants.CORRELATION_ID_HEADER, "test-corr-abc"))
                .andExpect(jsonPath("$.id").value(100))
                .andExpect(jsonPath("$.roomId").value(1))
                .andExpect(jsonPath("$.roomName").value("Taj Mahal"))
                .andExpect(jsonPath("$.userId").value(10))
                .andExpect(jsonPath("$.reason").value("Important Meeting"))
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
    }

    @Test
    @DisplayName("API Contract: Error response includes status, error, errorCode, message, path, and correlationId")
    void testErrorResponseContract() throws Exception {
        when(bookingService.getBooking(999L, 10L))
                .thenThrow(new BookingNotFoundException(999L));

        mockMvc.perform(get("/api/bookings/999")
                        .with(userAuth(10L))
                        .header("X-Correlation-Id", "error-corr-xyz"))
                .andExpect(status().isNotFound())
                .andExpect(header().string(CommonConstants.CORRELATION_ID_HEADER, "error-corr-xyz"))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("NOT_FOUND"))
                .andExpect(jsonPath("$.errorCode").value("BOOKING_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Booking with id '999' was not found"))
                .andExpect(jsonPath("$.path").value("/api/bookings/999"))
                .andExpect(jsonPath("$.correlationId").value("error-corr-xyz"));
    }

    @Test
    @DisplayName("API Contract: Room creation direct contract")
    void testRoomCreationContract() throws Exception {
        CreateRoomRequest request = CreateRoomRequest.builder()
                .name("Kaveri")
                .capacity(12)
                .description("Boardroom")
                .locationId(1L)
                .build();

        RoomResponse response = RoomResponse.builder()
                .id(5L)
                .name("Kaveri")
                .capacity(12)
                .description("Boardroom")
                .status(RoomStatus.AVAILABLE)
                .location(LocationResponse.builder().id(1L).name("Mumbai").code("MUM").build())
                .active(true)
                .build();

        when(roomService.createRoom(eq(1L), any(CreateRoomRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/rooms")
                        .with(adminAuth(1L))
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(header().exists(CommonConstants.CORRELATION_ID_HEADER))
                .andExpect(jsonPath("$.id").value(5))
                .andExpect(jsonPath("$.name").value("Kaveri"))
                .andExpect(jsonPath("$.capacity").value(12));
    }
}
