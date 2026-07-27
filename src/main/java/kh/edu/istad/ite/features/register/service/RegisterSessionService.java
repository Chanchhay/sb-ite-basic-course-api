package kh.edu.istad.ite.features.register.service;

import kh.edu.istad.ite.features.register.dto.request.CashMovementRequest;
import kh.edu.istad.ite.features.register.dto.request.CloseSessionRequest;
import kh.edu.istad.ite.features.register.dto.request.OpenSessionRequest;
import kh.edu.istad.ite.features.register.dto.response.CashMovementResponse;
import kh.edu.istad.ite.features.register.dto.response.RegisterSessionResponse;

import java.util.List;

public interface RegisterSessionService {
    RegisterSessionResponse openSession(Long registerId, OpenSessionRequest request, String cashierId);
    RegisterSessionResponse closeSession(Long sessionId, CloseSessionRequest request);
    RegisterSessionResponse getSessionSummary(Long sessionId);
    CashMovementResponse addCashMovement(Long sessionId, CashMovementRequest request);
    List<CashMovementResponse> getCashMovements(Long sessionId);
}
