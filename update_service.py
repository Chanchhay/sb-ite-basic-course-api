import re

with open('src/main/java/kh/edu/istad/ite/features/register/service/impl/RegisterSessionServiceImpl.java', 'r') as f:
    content = f.read()

# Imports
new_imports = """
import kh.edu.istad.ite.config.props.KeycloakAdminClientProps;
import kh.edu.istad.ite.features.business.entity.Business;
import kh.edu.istad.ite.features.business.repository.BusinessRepository;
import kh.edu.istad.ite.features.order.repository.OrderRepository;
import kh.edu.istad.ite.features.user.entity.UserProfile;
import kh.edu.istad.ite.features.user.repository.UserProfileRepository;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.representations.idm.UserRepresentation;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;
"""
content = content.replace("import java.util.stream.Collectors;", "import java.util.stream.Collectors;\n" + new_imports)

# Dependencies
deps = """
    private final CashRegisterRepository registerRepository;
    private final RegisterSessionRepository sessionRepository;
    private final CashMovementRepository movementRepository;
    private final UserProfileRepository userProfileRepository;
    private final BusinessRepository businessRepository;
    private final OrderRepository orderRepository;
    private final Keycloak keycloak;
    private final KeycloakAdminClientProps props;
"""
content = re.sub(
    r'private final CashRegisterRepository registerRepository;\n\s+private final RegisterSessionRepository sessionRepository;\n\s+private final CashMovementRepository movementRepository;',
    deps.strip(),
    content
)

# openSession
open_session = """
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
                .openingBalance(request.getOpeningBalance())
                .status(SessionStatus.OPEN)
                .note(request.getNote())
                .build();

        session = sessionRepository.save(session);
        return mapToResponse(session, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
    }
"""
content = re.sub(
    r'@Override\n\s+@Transactional\n\s+public RegisterSessionResponse openSession\(.*?\}\n',
    open_session.strip() + "\n",
    content,
    flags=re.DOTALL
)

# mapToResponse
map_to_response = """
    private RegisterSessionResponse mapToResponse(RegisterSession session, BigDecimal totalCashSales, BigDecimal totalPaidIn, BigDecimal totalPaidOut) {
        BigDecimal expected = session.getExpectedAmount() != null ? session.getExpectedAmount() :
                session.getOpeningBalance().add(totalCashSales).add(totalPaidIn).subtract(totalPaidOut);

        BigDecimal diff = session.getDifferenceAmount();
        String reconStatus = "MATCHED";
        if (diff != null) {
            if (diff.compareTo(BigDecimal.ZERO) > 0) reconStatus = "OVER";
            else if (diff.compareTo(BigDecimal.ZERO) < 0) reconStatus = "SHORT";
        }

        int orderCount = 0;
        if (session.getUserId() != null) {
            LocalDateTime start = LocalDateTime.ofInstant(session.getOpenedAt(), ZoneId.systemDefault());
            LocalDateTime end = session.getClosedAt() != null 
                    ? LocalDateTime.ofInstant(session.getClosedAt(), ZoneId.systemDefault()) 
                    : LocalDateTime.now();
            orderCount = (int) orderRepository.countByCashierIdAndCreatedDateBetween(UUID.fromString(session.getUserId()), start, end);
        }

        String cashierName = null;
        if (session.getUserId() != null) {
            try {
                UserResource userResource = keycloak.realm(props.getTargetRealm())
                        .users()
                        .get(session.getUserId());
                UserRepresentation keycloakUser = userResource.toRepresentation();
                cashierName = (keycloakUser.getFirstName() != null ? keycloakUser.getFirstName() : "") + 
                              (keycloakUser.getLastName() != null ? " " + keycloakUser.getLastName() : "");
                cashierName = cashierName.trim();
                if (cashierName.isEmpty()) {
                    cashierName = keycloakUser.getUsername();
                }
            } catch (Exception e) {
                cashierName = "Unknown";
            }
        }

        return RegisterSessionResponse.builder()
                .id(session.getId())
                .registerId(session.getRegister().getId())
                .registerName(session.getRegister().getName())
                .userId(session.getUserId())
                .cashierName(cashierName)
                .businessId(session.getBusinessId())
                .orderCount(orderCount)
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
"""

content = re.sub(
    r'private RegisterSessionResponse mapToResponse\(RegisterSession session, BigDecimal totalCashSales, BigDecimal totalPaidIn, BigDecimal totalPaidOut\) \{.*?^\s*\}',
    map_to_response.strip(),
    content,
    flags=re.DOTALL | re.MULTILINE
)

with open('src/main/java/kh/edu/istad/ite/features/register/service/impl/RegisterSessionServiceImpl.java', 'w') as f:
    f.write(content)

