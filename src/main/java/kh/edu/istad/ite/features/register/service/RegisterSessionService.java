package kh.edu.istad.ite.features.register.service;

import kh.edu.istad.ite.features.register.dto.request.CashMovementRequest;
import kh.edu.istad.ite.features.register.dto.request.CloseSessionRequest;
import kh.edu.istad.ite.features.register.dto.request.OpenSessionRequest;
import kh.edu.istad.ite.features.register.dto.response.CashMovementResponse;
import kh.edu.istad.ite.features.register.dto.response.RegisterSessionResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface RegisterSessionService {
    RegisterSessionResponse openSession(OpenSessionRequest request, String userId);
    RegisterSessionResponse closeSession(Long sessionId, CloseSessionRequest request);
    RegisterSessionResponse getCurrentSession(String userId);
    Page<RegisterSessionResponse> listSessions(String userId, Pageable pageable);
    RegisterSessionResponse joinSession(Long sessionId, String userId);
    RegisterSessionResponse getSessionSummary(Long sessionId);
    CashMovementResponse addCashMovement(Long sessionId, CashMovementRequest request);
    List<CashMovementResponse> getCashMovements(Long sessionId);
}
