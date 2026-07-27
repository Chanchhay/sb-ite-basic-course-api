package kh.edu.istad.ite.features.register.service.impl;

import kh.edu.istad.ite.features.register.dto.request.CashMovementRequest;
import kh.edu.istad.ite.features.register.dto.request.CloseSessionRequest;
import kh.edu.istad.ite.features.register.dto.request.OpenSessionRequest;
import kh.edu.istad.ite.features.register.dto.response.CashMovementResponse;
import kh.edu.istad.ite.features.register.dto.response.RegisterSessionResponse;
import kh.edu.istad.ite.features.register.entity.*;
import kh.edu.istad.ite.features.register.repository.CashMovementRepository;
import kh.edu.istad.ite.features.register.repository.CashRegisterRepository;
import kh.edu.istad.ite.features.register.repository.RegisterSessionRepository;
import kh.edu.istad.ite.features.register.service.RegisterSessionService;
import kh.edu.istad.ite.shared.enums.CashMovementType;
import kh.edu.istad.ite.shared.enums.RegisterStatus;
import kh.edu.istad.ite.shared.enums.SessionStatus;
import kh.edu.istad.ite.shared.exception.AppGlobalException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RegisterSessionServiceImpl implements RegisterSessionService {

    private final CashRegisterRepository registerRepository;
    private final RegisterSessionRepository sessionRepository;
    private final CashMovementRepository movementRepository;

    @Override
    @Transactional
    public RegisterSessionResponse openSession(Long registerId, OpenSessionRequest request, String cashierId) {
        CashRegister register = registerRepository.findById(registerId)
                .orElseThrow(() -> new ResponseStatusException( HttpStatus.NOT_FOUND,"Cash register not found"));

        if (register.getStatus() == RegisterStatus.OPEN) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cash register is already open");
        }

        sessionRepository.findByCashierIdAndStatus(cashierId, SessionStatus.OPEN).ifPresent(s -> {
            throw new ResponseStatusException( HttpStatus.BAD_REQUEST, "Cashier already has an active open session");
        });

        register.setStatus(RegisterStatus.OPEN);
        registerRepository.save(register);

        RegisterSession session = RegisterSession.builder()
                .register(register)
                .cashierId(cashierId)
                .openedAt(Instant.now())
                .openingBalance(request.getOpeningBalance())
                .status(SessionStatus.OPEN)
                .note(request.getNote())
                .build();

        session = sessionRepository.save(session);
        return mapToResponse(session, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
    }

    @Override
    @Transactional
    public RegisterSessionResponse closeSession(Long sessionId, CloseSessionRequest request) {
        RegisterSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,"Register session not found"));

        if (session.getStatus() == SessionStatus.CLOSED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Session is already closed");
        }

        BigDecimal totalCashSales = BigDecimal.ZERO; // Placeholder for Cash sales query from Order feature
        BigDecimal totalPaidIn = movementRepository.sumAmountBySessionIdAndType(sessionId, CashMovementType.PAID_IN);
        BigDecimal totalPaidOut = movementRepository.sumAmountBySessionIdAndType(sessionId, CashMovementType.PAID_OUT);

        BigDecimal expected = session.getOpeningBalance()
                .add(totalCashSales)
                .add(totalPaidIn)
                .subtract(totalPaidOut);

        BigDecimal actual = request.getActualAmount();
        BigDecimal difference = actual.subtract(expected);

        session.setClosedAt(Instant.now());
        session.setExpectedAmount(expected);
        session.setActualAmount(actual);
        session.setDifferenceAmount(difference);
        session.setStatus(SessionStatus.CLOSED);
        if (request.getClosingNote() != null) {
            session.setNote(session.getNote() != null ? session.getNote() + " | " + request.getClosingNote() : request.getClosingNote());
        }

        CashRegister register = session.getRegister();
        register.setStatus(RegisterStatus.CLOSED);
        registerRepository.save(register);

        sessionRepository.save(session);

        return mapToResponse(session, totalCashSales, totalPaidIn, totalPaidOut);
    }

    @Override
    @Transactional(readOnly = true)
    public RegisterSessionResponse getSessionSummary(Long sessionId) {
        RegisterSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,"Register session not found"));

        BigDecimal totalCashSales = BigDecimal.ZERO;
        BigDecimal totalPaidIn = movementRepository.sumAmountBySessionIdAndType(sessionId, CashMovementType.PAID_IN);
        BigDecimal totalPaidOut = movementRepository.sumAmountBySessionIdAndType(sessionId, CashMovementType.PAID_OUT);

        return mapToResponse(session, totalCashSales, totalPaidIn, totalPaidOut);
    }

    @Override
    @Transactional
    public CashMovementResponse addCashMovement(Long sessionId, CashMovementRequest request) {
        RegisterSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Register session not found"));

        if (session.getStatus() == SessionStatus.CLOSED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot add cash movement to a closed session");
        }

        CashMovement movement = CashMovement.builder()
                .session(session)
                .type(request.getType())
                .amount(request.getAmount())
                .reason(request.getReason())
                .build();

        movement = movementRepository.save(movement);

        return CashMovementResponse.builder()
                .id(movement.getId())
                .sessionId(sessionId)
                .type(movement.getType())
                .amount(movement.getAmount())
                .reason(movement.getReason())
                .createdAt(movement.getCreatedDate())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<CashMovementResponse> getCashMovements(Long sessionId) {
        return movementRepository.findBySessionId(sessionId).stream()
                .map(m -> CashMovementResponse.builder()
                        .id(m.getId())
                        .sessionId(sessionId)
                        .type(m.getType())
                        .amount(m.getAmount())
                        .reason(m.getReason())
                        .createdAt(m.getCreatedDate())
                        .build())
                .collect(Collectors.toList());
    }

    private RegisterSessionResponse mapToResponse(RegisterSession session, BigDecimal totalCashSales, BigDecimal totalPaidIn, BigDecimal totalPaidOut) {
        BigDecimal expected = session.getExpectedAmount() != null ? session.getExpectedAmount() :
                session.getOpeningBalance().add(totalCashSales).add(totalPaidIn).subtract(totalPaidOut);

        BigDecimal diff = session.getDifferenceAmount();
        String reconStatus = "MATCHED";
        if (diff != null) {
            if (diff.compareTo(BigDecimal.ZERO) > 0) reconStatus = "OVER";
            else if (diff.compareTo(BigDecimal.ZERO) < 0) reconStatus = "SHORT";
        }

        return RegisterSessionResponse.builder()
                .id(session.getId())
                .registerId(session.getRegister().getId())
                .registerName(session.getRegister().getName())
                .cashierId(session.getCashierId())
                .openedAt(session.getOpenedAt())
                .closedAt(session.getClosedAt())
                .openingBalance(session.getOpeningBalance())
                .totalCashSales(totalCashSales)
                .totalPaidIn(totalPaidIn)
                .totalPaidOut(totalPaidOut)
                .expectedAmount(expected)
                .actualAmount(session.getActualAmount())
                .differenceAmount(diff)
                .reconciliationStatus(reconStatus)
                .status(session.getStatus())
                .note(session.getNote())
                .build();
    }
}
