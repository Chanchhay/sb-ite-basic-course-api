package kh.edu.istad.ite.features.register.controller;

import jakarta.validation.Valid;
import kh.edu.istad.ite.features.register.dto.request.CashMovementRequest;
import kh.edu.istad.ite.features.register.dto.request.CloseSessionRequest;
import kh.edu.istad.ite.features.register.dto.request.OpenSessionRequest;
import kh.edu.istad.ite.features.register.dto.response.CashMovementResponse;
import kh.edu.istad.ite.features.register.dto.response.RegisterSessionResponse;
import kh.edu.istad.ite.features.register.service.RegisterSessionService;
import kh.edu.istad.ite.shared.helper.AuthHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import kh.edu.istad.ite.shared.enums.SessionStatus;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class RegisterSessionController {

    private final RegisterSessionService sessionService;

    @PostMapping("/sessions/open")
    public ResponseEntity<RegisterSessionResponse> openSession(
            @Valid @RequestBody OpenSessionRequest request) {

        String userId = AuthHelper.currentUserId().toString();
        RegisterSessionResponse response = sessionService.openSession(request, userId);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(response);
    }

    @PostMapping("/sessions/{sessionId}/close")
    public ResponseEntity<RegisterSessionResponse> closeSession(
            @PathVariable Long sessionId,
            @Valid @RequestBody CloseSessionRequest request) {
        return ResponseEntity.ok(sessionService.closeSession(sessionId, request));
    }

    @GetMapping("/sessions/{sessionId}/summary")
    public ResponseEntity<RegisterSessionResponse> getSessionSummary(@PathVariable Long sessionId) {
        return ResponseEntity.ok(sessionService.getSessionSummary(sessionId));
    }

    @PostMapping("/sessions/{sessionId}/cash-movements")
    public ResponseEntity<CashMovementResponse> addCashMovement(
            @PathVariable Long sessionId,
            @Valid @RequestBody CashMovementRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(sessionService.addCashMovement(sessionId, request));
    }

    @GetMapping("/sessions/{sessionId}/cash-movements")
    public ResponseEntity<List<CashMovementResponse>> getCashMovements(@PathVariable Long sessionId) {
        return ResponseEntity.ok(sessionService.getCashMovements(sessionId));
    }

    @GetMapping("/sessions/current")
    public ResponseEntity<RegisterSessionResponse> getCurrentSession() {
        String userId = AuthHelper.currentUserId().toString();
        RegisterSessionResponse response = sessionService.getCurrentSession(userId);
        if (response == null) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(response);
    }

    @PostMapping("/sessions/{sessionId}/join")
    public ResponseEntity<RegisterSessionResponse> joinSession(@PathVariable Long sessionId) {
        String userId = AuthHelper.currentUserId().toString();
        RegisterSessionResponse response = sessionService.joinSession(sessionId, userId);
        return ResponseEntity.ok(response);
    }
}
