package kh.edu.istad.ite.features.register.service.impl;

import jakarta.persistence.criteria.Predicate;
import kh.edu.istad.ite.config.filter.RequestDto;
import kh.edu.istad.ite.config.props.KeycloakAdminClientProps;
import kh.edu.istad.ite.config.specification.FilterSpecification;
import kh.edu.istad.ite.features.business.entity.Business;
import kh.edu.istad.ite.features.business.repository.BusinessRepository;
import kh.edu.istad.ite.features.register.dto.request.CashMovementRequest;
import kh.edu.istad.ite.features.register.dto.request.CloseSessionRequest;
import kh.edu.istad.ite.features.register.dto.request.OpenSessionRequest;
import kh.edu.istad.ite.features.register.dto.response.CashMovementResponse;
import kh.edu.istad.ite.features.register.dto.response.RegisterSessionMetrics;
import kh.edu.istad.ite.features.register.dto.response.RegisterSessionResponse;
import kh.edu.istad.ite.features.register.dto.response.RegisterSessionSearchResponse;
import kh.edu.istad.ite.features.register.entity.*;
import kh.edu.istad.ite.features.register.repository.CashMovementRepository;
import kh.edu.istad.ite.features.register.repository.CashRegisterRepository;
import kh.edu.istad.ite.features.register.repository.RegisterSessionRepository;
import kh.edu.istad.ite.features.register.service.RegisterSessionService;
import kh.edu.istad.ite.features.user.entity.UserProfile;
import kh.edu.istad.ite.features.user.repository.UserProfileRepository;
import kh.edu.istad.ite.shared.enums.CashMovementType;
import kh.edu.istad.ite.shared.enums.RegisterStatus;
import kh.edu.istad.ite.shared.enums.SessionStatus;
import kh.edu.istad.ite.shared.dto.PageResponse;
import kh.edu.istad.ite.shared.exception.AppGlobalException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RegisterSessionServiceImpl implements RegisterSessionService {

    private final CashRegisterRepository registerRepository;
    private final RegisterSessionRepository sessionRepository;
    private final FilterSpecification<RegisterSession> filterSpecification;
    private final CashMovementRepository movementRepository;
    private final UserProfileRepository userProfileRepository;
    private final BusinessRepository businessRepository;
    private final kh.edu.istad.ite.features.order.repository.SaleRepository saleRepository;
    private final Keycloak keycloak;
    private final KeycloakAdminClientProps props;

    @Override
    @Transactional
    public RegisterSessionResponse openSession(OpenSessionRequest request, String userId) {
        UUID userUuid = UUID.fromString(userId);
        Business business = businessRepository.findByKeycloakUserId(userUuid).orElse(null);

        if (business == null) {
            UserProfile profile = userProfileRepository.findById(userUuid)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User profile not found"));
            business = profile.getBusiness();
        }

        if (business == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "User is not associated with any business");
        }
        
        UUID businessId = business.getId();

        CashRegister register = registerRepository.findByBusinessId(businessId)
                .orElseGet(() -> {
                    CashRegister newRegister = CashRegister.builder()
                            .name("Main Register")
                            .businessId(businessId)
                            .status(RegisterStatus.CLOSED)
                            .build();
                    return registerRepository.save(newRegister);
                });

        // Checked before the register's own status: a drawer that is already
        // open is not an error for a second cashier, it is the drawer they are
        // meant to share. Rejecting first would leave this branch unreachable
        // and send every cashier of the shop away with "already open".
        RegisterSession existingSession = sessionRepository.findByRegisterIdAndStatus(register.getId(), SessionStatus.OPEN).orElse(null);
        if (existingSession != null) {
            return joinSession(existingSession.getId(), userId);
        }

        if (register.getStatus() == RegisterStatus.OPEN) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cash register is already open");
        }

        sessionRepository.findByUserIdAndStatus(userId, SessionStatus.OPEN).ifPresent(s -> {
            throw new ResponseStatusException( HttpStatus.BAD_REQUEST, "Cashier already has an active open session");
        });

        register.setStatus(RegisterStatus.OPEN);
        registerRepository.save(register);

        RegisterSession session = RegisterSession.builder()
                .register(register)
                .userId(userId)
                .businessId(businessId)
                .openedAt(Instant.now())
                // Fixed now: the drawer holds physical notes, so a later
                // base-currency change must not restate this session.
                .currency(businessRepository.findById(businessId)
                        .map(kh.edu.istad.ite.features.business.entity.Business::getBaseCurrency)
                        .orElse(null))
                .openingBalance(request.getOpeningBalance())
                .status(SessionStatus.OPEN)
                .note(request.getNote())
                .participants(new java.util.HashSet<>(java.util.Collections.singletonList(userId)))
                .build();

        session = sessionRepository.save(session);
        return mapToResponse(session, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
    }

    @Override
    @Transactional
    public RegisterSessionResponse closeSession(Long sessionId, CloseSessionRequest request, String userId) {
        RegisterSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,"Register session not found"));

        // Session ids are sequential, so without this a caller could close
        // another shop's drawer by counting upwards. Joining already checks
        // this; ending a shift is the heavier action of the two.
        Business business = resolveBusiness(userId);
        if (business == null || !business.getId().equals(session.getBusinessId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Session does not belong to user's business");
        }

        if (session.getStatus() == SessionStatus.CLOSED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Session is already closed");
        }

        BigDecimal totalCashSales = saleRepository.sumCashSalesByRegisterSessionId(sessionId);
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
    public RegisterSessionResponse getCurrentSession(String userId) {
        UUID userUuid = UUID.fromString(userId);
        Business business = businessRepository.findByKeycloakUserId(userUuid).orElse(null);

        if (business == null) {
            UserProfile profile = userProfileRepository.findById(userUuid).orElse(null);
            if (profile != null) {
                business = profile.getBusiness();
            }
        }

        if (business == null) {
            return null;
        }

        CashRegister register = registerRepository.findByBusinessId(business.getId()).orElse(null);
        if (register == null) {
            return null;
        }

        RegisterSession session = sessionRepository.findByRegisterIdAndStatus(register.getId(), SessionStatus.OPEN).orElse(null);
        if (session == null) {
            return null;
        }

        return getSessionSummary(session.getId());
    }


    @Override
    @Transactional(readOnly = true)
    public PageResponse<RegisterSessionResponse> listSessions(String userId, Pageable pageable) {
        Business business = resolveBusiness(userId);
        if (business == null) {
            return PageResponse.from(Page.empty(pageable));
        }

        Page<RegisterSessionResponse> page = summarizePage(
                sessionRepository.findByBusinessId(business.getId(), pageable), pageable);
        return PageResponse.from(page);
    }


    @Override
    @Transactional(readOnly = true)
    public RegisterSessionSearchResponse searchSessions(
            String userId, RequestDto requestDto, String search, Pageable pageable) {

        Business business = resolveBusiness(userId);
        if (business == null) {
            return new RegisterSessionSearchResponse(
                    PageResponse.from(new PageImpl<>(List.of(), pageable, 0)),
                    new RegisterSessionMetrics(
                            0, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO));
        }

        UUID businessId = business.getId();

        // Always ANDed, never negotiable: everything below narrows within one
        // business, and no filter a caller sends can widen past it.
        Specification<RegisterSession> spec =
                (root, query, cb) -> cb.equal(root.get("businessId"), businessId);

        if (requestDto != null
                && requestDto.getSearchRequestDto() != null
                && !requestDto.getSearchRequestDto().isEmpty()) {
            spec = spec.and(filterSpecification.getSearchSpecificationDynamic(
                    requestDto.getSearchRequestDto(),
                    requestDto.getGlobalOperator() == null
                            ? RequestDto.GlobalOperator.AND
                            : requestDto.getGlobalOperator()));
        }

        if (search != null && !search.isBlank()) {
            spec = spec.and(freeTextSpec(search.trim()));
        }

        Page<RegisterSession> page = sessionRepository.findAll(spec, pageable);

        return new RegisterSessionSearchResponse(
                PageResponse.from(summarizePage(page, pageable)),
                metricsFor(businessId, spec));
    }


    private Specification<RegisterSession> freeTextSpec(String search) {
        String like = "%" + search.toLowerCase() + "%";
        List<String> cashierIds = cashierIdsMatching(search);

        return (root, query, cb) -> {
            List<Predicate> matches = new ArrayList<>();

            matches.add(cb.like(cb.lower(root.get("userId")), like));
            matches.add(cb.like(cb.lower(root.get("note")), like));
            matches.add(cb.like(cb.lower(root.get("register").get("name")), like));
            matches.add(cb.like(cb.lower(root.get("id").as(String.class)), like));

            if (!cashierIds.isEmpty()) {
                matches.add(root.get("userId").in(cashierIds));
                // A cashier who joined a drawer someone else opened is not in
                // `userId`, so searching their name would otherwise miss every
                // shift they actually worked. A subquery rather than a join:
                // joining the collection would multiply rows and break paging.
                matches.add(cb.exists(participantSubquery(root, query, cb, cashierIds)));
            }

            String word = search.toLowerCase();
            if ("over".startsWith(word)) {
                matches.add(cb.greaterThan(root.get("differenceAmount"), BigDecimal.ZERO));
            }
            if ("short".startsWith(word)) {
                matches.add(cb.lessThan(root.get("differenceAmount"), BigDecimal.ZERO));
            }
            if ("matched".startsWith(word) || "balanced".startsWith(word)) {
                matches.add(cb.equal(root.get("differenceAmount"), BigDecimal.ZERO));
            }

            return cb.or(matches.toArray(new Predicate[0]));
        };
    }

    /** Whether this session has a participant among the given user ids. */
    private static jakarta.persistence.criteria.Subquery<String> participantSubquery(
            jakarta.persistence.criteria.Root<RegisterSession> root,
            jakarta.persistence.criteria.CriteriaQuery<?> query,
            jakarta.persistence.criteria.CriteriaBuilder cb,
            List<String> cashierIds) {

        jakarta.persistence.criteria.Subquery<String> sub = query.subquery(String.class);
        jakarta.persistence.criteria.Root<RegisterSession> other = sub.from(RegisterSession.class);
        jakarta.persistence.criteria.Join<RegisterSession, String> participant =
                other.join("participants");

        return sub.select(participant)
                .where(cb.equal(other.get("id"), root.get("id")), participant.in(cashierIds));
    }

    /** The user ids whose Keycloak name or username matches what was typed. */
    private List<String> cashierIdsMatching(String search) {
        try {
            return keycloak.realm(props.getTargetRealm())
                    .users()
                    .search(search, 0, 50).stream()
                    .map(UserRepresentation::getId)
                    .filter(java.util.Objects::nonNull)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            // A search box that finds fewer rows is a worse search box; one
            // that fails the whole screen because the identity server is slow
            // is a broken one.
            log.warn("Could not search Keycloak for cashiers matching '{}'", search, e);
            return List.of();
        }
    }


    private RegisterSessionMetrics metricsFor(
            UUID businessId, Specification<RegisterSession> spec) {

        List<RegisterSession> matched = sessionRepository.findAll(spec);
        long activeCount =
                sessionRepository.countByBusinessIdAndStatus(businessId, SessionStatus.OPEN);

        if (matched.isEmpty()) {
            return new RegisterSessionMetrics(
                    activeCount, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        }

        BigDecimal totalOpening = BigDecimal.ZERO;
        BigDecimal totalDiscrepancies = BigDecimal.ZERO;
        List<Long> ids = new ArrayList<>(matched.size());

        for (RegisterSession session : matched) {
            ids.add(session.getId());
            totalOpening = totalOpening.add(orZero(session.getOpeningBalance()));
            // Absolute: over and short are both "out", and netting them off
            // would report a day that was ten over and ten short as balanced.
            totalDiscrepancies = totalDiscrepancies.add(orZero(session.getDifferenceAmount()).abs());
        }

        BigDecimal totalCashSales = orZero(saleRepository.sumCashSalesByRegisterSessionIds(ids));

        return new RegisterSessionMetrics(
                activeCount, totalOpening, totalCashSales, totalDiscrepancies);
    }

    private static BigDecimal orZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }


    private Page<RegisterSessionResponse> summarizePage(
            Page<RegisterSession> page, Pageable pageable) {

        if (page.isEmpty()) {
            // Guarded rather than left to the stream below: the grouped
            // movement query takes an IN list, and an empty one is not valid
            // SQL. The page's own totals still describe the full result.
            return new PageImpl<>(List.of(), pageable, page.getTotalElements());
        }

        List<Long> sessionIds = page.getContent().stream()
                .map(RegisterSession::getId)
                .collect(Collectors.toList());

        Map<Long, Map<CashMovementType, BigDecimal>> movementTotals = new HashMap<>();
        for (CashMovementRepository.SessionTypeTotal row : movementRepository.sumAmountBySessionIds(sessionIds)) {
            movementTotals
                    .computeIfAbsent(row.getSessionId(), id -> new EnumMap<>(CashMovementType.class))
                    .put(row.getType(), row.getTotal());
        }

        Map<String, String> cashierNames = new HashMap<>();
        return page.map(session -> {
            Map<CashMovementType, BigDecimal> totals =
                    movementTotals.getOrDefault(session.getId(), Map.of());
            return summarize(
                    session,
                    totals.getOrDefault(CashMovementType.PAID_IN, BigDecimal.ZERO),
                    totals.getOrDefault(CashMovementType.PAID_OUT, BigDecimal.ZERO),
                    cashierNames);
        });
    }

    /**
     * Everyone whose takings belong to this drawer, the opener first.
     *
     * The opener is unioned in rather than assumed to be a participant row:
     * sessions opened before the drawer became shareable have none, and their
     * cash still has to be counted.
     */
    private static java.util.Set<String> cashierIdsOf(RegisterSession session) {
        java.util.Set<String> ids = new java.util.LinkedHashSet<>();

        if (session.getUserId() != null) {
            ids.add(session.getUserId());
        }
        if (session.getParticipants() != null) {
            session.getParticipants().stream().filter(java.util.Objects::nonNull).forEach(ids::add);
        }

        return ids;
    }

    private Business resolveBusiness(String userId) {
        UUID userUuid = UUID.fromString(userId);
        Business business = businessRepository.findByKeycloakUserId(userUuid).orElse(null);
        if (business == null) {
            UserProfile profile = userProfileRepository.findById(userUuid).orElse(null);
            if (profile != null) {
                business = profile.getBusiness();
            }
        }
        return business;
    }

    @Override
    @Transactional
    public RegisterSessionResponse joinSession(Long sessionId, String userId) {
        RegisterSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Register session not found"));
        
        UUID userUuid = UUID.fromString(userId);
        Business business = businessRepository.findByKeycloakUserId(userUuid).orElse(null);
        if (business == null) {
            UserProfile profile = userProfileRepository.findById(userUuid).orElse(null);
            if (profile != null) {
                business = profile.getBusiness();
            }
        }
        
        if (business == null || !business.getId().equals(session.getBusinessId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Session does not belong to user's business");
        }

        if (session.getStatus() != SessionStatus.OPEN) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Session is not open");
        }

        session.getParticipants().add(userId);
        session = sessionRepository.save(session);
        
        return getSessionSummary(session.getId());
    }

    @Override
    @Transactional(readOnly = true)
    public RegisterSessionResponse getSessionSummary(Long sessionId) {
        RegisterSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,"Register session not found"));

        BigDecimal totalPaidIn = movementRepository.sumAmountBySessionIdAndType(sessionId, CashMovementType.PAID_IN);
        BigDecimal totalPaidOut = movementRepository.sumAmountBySessionIdAndType(sessionId, CashMovementType.PAID_OUT);

        return summarize(session, totalPaidIn, totalPaidOut, new HashMap<>());
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
                .currency(session.getCurrency())
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
                        .currency(m.getSession() == null ? null : m.getSession().getCurrency())
                        .id(m.getId())
                        .sessionId(sessionId)
                        .type(m.getType())
                        .amount(m.getAmount())
                        .reason(m.getReason())
                        .createdAt(m.getCreatedDate())
                        .build())
                .collect(Collectors.toList());
    }


    private RegisterSessionResponse summarize(
            RegisterSession session,
            BigDecimal totalPaidIn,
            BigDecimal totalPaidOut,
            Map<String, String> cashierNames) {

        return mapToResponse(
                session,
                saleRepository.sumCashSalesByRegisterSessionId(session.getId()),
                totalPaidIn,
                totalPaidOut,
                cashierNames);
    }

    /**
     * A cashier's display name, cached for the page being rendered.
     *
     * One Keycloak round trip per distinct user, not per row: a shared drawer
     * repeats the same handful of people down the whole history.
     */
    private String resolveCashierName(String userId, Map<String, String> cache) {
        String cached = cache.get(userId);
        if (cached != null) {
            return cached;
        }

        String name;
        try {
            UserResource userResource = keycloak.realm(props.getTargetRealm())
                    .users()
                    .get(userId);
            UserRepresentation keycloakUser = userResource.toRepresentation();
            name = (keycloakUser.getFirstName() != null ? keycloakUser.getFirstName() : "") +
                   (keycloakUser.getLastName() != null ? " " + keycloakUser.getLastName() : "");
            name = name.trim();
            if (name.isEmpty()) {
                name = keycloakUser.getUsername();
            }
        } catch (Exception e) {
            name = "Unknown";
        }

        cache.put(userId, name);
        return name;
    }

    private RegisterSessionResponse mapToResponse(RegisterSession session, BigDecimal totalCashSales, BigDecimal totalPaidIn, BigDecimal totalPaidOut) {
        return mapToResponse(session, totalCashSales, totalPaidIn, totalPaidOut, new HashMap<>());
    }

    private RegisterSessionResponse mapToResponse(RegisterSession session, BigDecimal totalCashSales, BigDecimal totalPaidIn, BigDecimal totalPaidOut, Map<String, String> cashierNames) {
        BigDecimal expected = session.getExpectedAmount() != null ? session.getExpectedAmount() :
                session.getOpeningBalance().add(totalCashSales).add(totalPaidIn).subtract(totalPaidOut);

        BigDecimal diff = session.getDifferenceAmount();
        String reconStatus = "MATCHED";
        if (diff != null) {
            if (diff.compareTo(BigDecimal.ZERO) > 0) reconStatus = "OVER";
            else if (diff.compareTo(BigDecimal.ZERO) < 0) reconStatus = "SHORT";
        }

        // Sales, not orders: an order that was never paid for put nothing in
        // the drawer, and this figure sits beside the cash it took.
        int orderCount = (int) saleRepository.countByRegisterSessionId(session.getId());

        List<String> names = cashierIdsOf(session).stream()
                .map(id -> resolveCashierName(id, cashierNames))
                .collect(Collectors.toList());
        // The opener stays the headline name; the rest are what the history
        // row counts its "+N" from.
        String cashierName = names.isEmpty() ? null : names.get(0);

        return RegisterSessionResponse.builder()
                .id(session.getId())
                .registerId(session.getRegister().getId())
                .registerName(session.getRegister().getName())
                .userId(session.getUserId())
                .cashierName(cashierName)
                .cashierNames(names)
                .businessId(session.getBusinessId())
                .orderCount(orderCount)
                .openedAt(session.getOpenedAt())
                .closedAt(session.getClosedAt())
                .currency(session.getCurrency())
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
