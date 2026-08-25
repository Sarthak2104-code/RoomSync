package com.roomsync.booking.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.roomsync.booking.dto.BookingResponse;
import com.roomsync.booking.dto.CreateBookingRequest;
import com.roomsync.booking.dto.RescheduleBookingRequest;
import com.roomsync.booking.entity.BookingStatus;
import com.roomsync.booking.exception.BookingNotFoundException;
import com.roomsync.booking.exception.BookingOverlapException;
import com.roomsync.booking.exception.UnauthorizedBookingOperationException;
import com.roomsync.booking.service.BookingService;
import com.roomsync.common.exception.GlobalExceptionHandler;
import com.roomsync.common.response.PageResponse;
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

import static com.roomsync.security.TestSecurityUtils.userAuth;
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

@WebMvcTest(BookingController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class BookingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private BookingService bookingService;

    @Test
    @DisplayName("POST /api/bookings - Should return 201 when request is valid")
    void shouldCreateBooking() throws Exception {
        CreateBookingRequest request = CreateBookingRequest.builder()
                .roomId(1L)
                .startTime(OffsetDateTime.parse("2026-08-20T10:00:00Z"))
                .endTime(OffsetDateTime.parse("2026-08-20T11:00:00Z"))
                .reason("Architecture Discussion")
                .build();

        BookingResponse response = BookingResponse.builder()
                .id(100L)
                .roomId(1L)
                .roomName("Taj Mahal")
                .userId(123L)
                .startTime(request.getStartTime())
                .endTime(request.getEndTime())
                .reason("Architecture Discussion")
                .status(BookingStatus.CONFIRMED)
                .build();

        when(bookingService.createBooking(eq(123L), any(CreateBookingRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/bookings")
                        .with(userAuth(123L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(100))
                .andExpect(jsonPath("$.roomId").value(1))
                .andExpect(jsonPath("$.userId").value(123))
                .andExpect(jsonPath("$.reason").value("Architecture Discussion"))
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
    }

    @Test
    @DisplayName("POST /api/bookings - Should return 409 when overlap occurs")
    void shouldReturn409OnOverlap() throws Exception {
        CreateBookingRequest request = CreateBookingRequest.builder()
                .roomId(1L)
                .startTime(OffsetDateTime.parse("2026-08-20T10:00:00Z"))
                .endTime(OffsetDateTime.parse("2026-08-20T11:00:00Z"))
                .reason("Overlap Conflict")
                .build();

        when(bookingService.createBooking(eq(123L), any(CreateBookingRequest.class)))
                .thenThrow(new BookingOverlapException("The requested time slot overlaps with an existing confirmed booking"));

        mockMvc.perform(post("/api/bookings")
                        .with(userAuth(123L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("CONFLICT"));
    }

    @Test
    @DisplayName("GET /api/bookings/{id} - Should return 200 for booking owner")
    void shouldGetBooking() throws Exception {
        BookingResponse response = BookingResponse.builder()
                .id(100L)
                .roomId(1L)
                .userId(123L)
                .reason("Client Review")
                .status(BookingStatus.CONFIRMED)
                .build();

        when(bookingService.getBooking(100L, 123L)).thenReturn(response);

        mockMvc.perform(get("/api/bookings/100")
                        .with(userAuth(123L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(100))
                .andExpect(jsonPath("$.userId").value(123));
    }

    @Test
    @DisplayName("GET /api/bookings/{id} - Should return 403 when user is not the owner")
    void shouldReturn403WhenNotOwner() throws Exception {
        when(bookingService.getBooking(100L, 456L))
                .thenThrow(new UnauthorizedBookingOperationException("User is not authorized to access this booking"));

        mockMvc.perform(get("/api/bookings/100")
                        .with(userAuth(456L)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("GET /api/bookings/{id} - Should return 404 when booking does not exist")
    void shouldReturn404WhenBookingNotFound() throws Exception {
        when(bookingService.getBooking(999L, 123L))
                .thenThrow(new BookingNotFoundException(999L));

        mockMvc.perform(get("/api/bookings/999")
                        .with(userAuth(123L)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("NOT_FOUND"));
    }

    @Test
    @DisplayName("GET /api/bookings/my - Should return 200 with paginated bookings")
    void shouldGetMyBookings() throws Exception {
        BookingResponse response = BookingResponse.builder()
                .id(100L)
                .roomId(1L)
                .userId(123L)
                .reason("Team Catchup")
                .status(BookingStatus.CONFIRMED)
                .build();

        PageResponse<BookingResponse> pageResponse = PageResponse.<BookingResponse>builder()
                .content(List.of(response))
                .page(0)
                .size(10)
                .totalElements(1)
                .totalPages(1)
                .first(true)
                .last(true)
                .build();

        when(bookingService.getMyBookings(eq(123L), any(Pageable.class))).thenReturn(pageResponse);

        mockMvc.perform(get("/api/bookings/my")
                        .with(userAuth(123L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(100))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @DisplayName("PUT /api/bookings/{id} - Should return 200 on successful reschedule")
    void shouldRescheduleBooking() throws Exception {
        RescheduleBookingRequest request = RescheduleBookingRequest.builder()
                .startTime(OffsetDateTime.parse("2026-08-20T14:00:00Z"))
                .endTime(OffsetDateTime.parse("2026-08-20T15:00:00Z"))
                .build();

        BookingResponse response = BookingResponse.builder()
                .id(100L)
                .roomId(1L)
                .userId(123L)
                .startTime(request.getStartTime())
                .endTime(request.getEndTime())
                .reason("Rescheduled Meeting")
                .status(BookingStatus.CONFIRMED)
                .build();

        when(bookingService.rescheduleBooking(eq(100L), eq(123L), any(RescheduleBookingRequest.class)))
                .thenReturn(response);

        mockMvc.perform(put("/api/bookings/100")
                        .with(userAuth(123L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(100));
    }


    @Test
    @DisplayName("PATCH /api/bookings/{id}/reschedule - Should return 200 on successful reschedule")
    void shouldRescheduleBookingViaPatch() throws Exception {
        RescheduleBookingRequest request = RescheduleBookingRequest.builder()
                .startTime(OffsetDateTime.parse("2026-08-20T14:00:00Z"))
                .endTime(OffsetDateTime.parse("2026-08-20T15:00:00Z"))
                .build();

        BookingResponse response = BookingResponse.builder()
                .id(100L)
                .roomId(1L)
                .userId(123L)
                .startTime(request.getStartTime())
                .endTime(request.getEndTime())
                .reason("Rescheduled Meeting")
                .status(BookingStatus.CONFIRMED)
                .build();

        when(bookingService.rescheduleBooking(eq(100L), eq(123L), any(RescheduleBookingRequest.class)))
                .thenReturn(response);

        mockMvc.perform(patch("/api/bookings/100/reschedule")
                        .with(userAuth(123L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(100));
    }

    @Test
    @DisplayName("DELETE /api/bookings/{id} - Should return 204 on cancellation")
    void shouldCancelBooking() throws Exception {
        doNothing().when(bookingService).cancelBooking(eq(100L), eq(123L), any());

        mockMvc.perform(delete("/api/bookings/100")
                        .with(userAuth(123L)))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("DELETE /api/bookings/{id} - Should return 204 with cancel reason body")
    void shouldCancelBookingWithReasonBody() throws Exception {
        com.roomsync.booking.dto.CancelBookingRequest request = com.roomsync.booking.dto.CancelBookingRequest.builder()
                .reason("Meeting cancelled by client")
                .build();

        doNothing().when(bookingService).cancelBooking(eq(100L), eq(123L), eq("Meeting cancelled by client"));

        mockMvc.perform(delete("/api/bookings/100")
                        .with(userAuth(123L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNoContent());
    }
}
