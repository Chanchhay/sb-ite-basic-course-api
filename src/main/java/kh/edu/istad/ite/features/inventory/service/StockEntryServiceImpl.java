package kh.edu.istad.ite.features.inventory.service;

import jakarta.persistence.criteria.Predicate;
import kh.edu.istad.ite.features.business.entity.Business;
import kh.edu.istad.ite.features.catalog.entity.Item;
import kh.edu.istad.ite.features.catalog.repository.ItemRepository;
import kh.edu.istad.ite.features.inventory.dto.CreateStockEntryRequest;
import kh.edu.istad.ite.features.inventory.dto.StockEntryResponse;
import kh.edu.istad.ite.features.inventory.dto.StockSummaryResponse;
import kh.edu.istad.ite.features.inventory.entity.StockEntry;
import kh.edu.istad.ite.features.inventory.mapper.StockEntryMapper;
import kh.edu.istad.ite.features.inventory.repository.StockEntryRepository;
import kh.edu.istad.ite.shared.enums.StockEntryType;
import kh.edu.istad.ite.shared.helper.BusinessHelper;
import kh.edu.istad.ite.shared.helper.TextHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class StockEntryServiceImpl implements StockEntryService {

    private static final BigDecimal ZERO_QUANTITY = BigDecimal.ZERO.setScale(3);

    private final BusinessHelper businessHelper;
    private final ItemRepository itemRepository;
    private final StockEntryRepository stockEntryRepository;
    private final StockEntryMapper stockEntryMapper;

    @Override
    @Transactional
    public StockEntryResponse createStockEntry(UUID businessId, CreateStockEntryRequest request) {
        Business business = businessHelper.findOwnedBusiness(businessId);
        Item product = findProduct(request.productId(), businessId);

        Optional<StockEntry> latestEntry = stockEntryRepository
                .findFirstByBusiness_IdAndProduct_IdOrderByCreatedDateDescIdDesc(businessId, product.getId());
        BigDecimal quantityBefore = latestEntry
                .map(StockEntry::getQuantityAfter)
                .orElse(ZERO_QUANTITY);
        BigDecimal quantityChange = normalizeQuantity(request.quantityChange());

        StockEntryType entryType = StockEntryType.valueOf(request.entryType());
        validateQuantityChange(entryType, quantityChange, latestEntry.isPresent());

        BigDecimal quantityAfter = quantityBefore.add(quantityChange).setScale(3, RoundingMode.HALF_UP);
        if (quantityAfter.compareTo(BigDecimal.ZERO) < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Stock quantity cannot become negative");
        }

        StockEntry stockEntry = new StockEntry();
        stockEntry.setBusiness(business);
        stockEntry.setProduct(product);
        stockEntry.setEntryType(entryType);
        stockEntry.setQuantityChange(quantityChange);
        stockEntry.setQuantityBefore(quantityBefore);
        stockEntry.setQuantityAfter(quantityAfter);
        stockEntry.setUnitCost(normalizeUnitCost(request.unitCost()));
        stockEntry.setBatchData(request.batchData());
        stockEntry.setReferenceType(TextHelper.trimToNull(request.referenceType()));
        stockEntry.setReferenceId(request.referenceId());
        stockEntry.setReferenceNumber(TextHelper.trimToNull(request.referenceNumber()));
        stockEntry.setReason(TextHelper.trimToNull(request.reason()));

        return stockEntryMapper.toResponse(stockEntryRepository.saveAndFlush(stockEntry));
    }

    @Override
    @Transactional
    public StockEntry recordSale(
            Business business,
            Item item,
            BigDecimal quantity,
            UUID orderId,
            String invoiceNumber
    ) {
        Optional<StockEntry> latestEntry = stockEntryRepository
                .findFirstByBusiness_IdAndProduct_IdOrderByCreatedDateDescIdDesc(business.getId(), item.getId());

        BigDecimal quantityBefore = latestEntry.map(StockEntry::getQuantityAfter).orElse(ZERO_QUANTITY);
        BigDecimal quantityChange = normalizeQuantity(quantity).negate();
        BigDecimal quantityAfter = quantityBefore.add(quantityChange).setScale(3, RoundingMode.HALF_UP);

        if (quantityAfter.compareTo(BigDecimal.ZERO) < 0) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Not enough stock for \"" + item.getName() + "\": " + quantityBefore + " left");
        }

        StockEntry stockEntry = new StockEntry();
        stockEntry.setBusiness(business);
        stockEntry.setProduct(item);
        stockEntry.setEntryType(StockEntryType.SALE);
        stockEntry.setQuantityChange(quantityChange);
        stockEntry.setQuantityBefore(quantityBefore);
        stockEntry.setQuantityAfter(quantityAfter);
        stockEntry.setUnitCost(latestEntry.map(StockEntry::getUnitCost).orElse(null));
        // The reference is what lets a shop trace a movement back to the till.
        stockEntry.setReferenceType("ORDER");
        stockEntry.setReferenceId(orderId);
        stockEntry.setReferenceNumber(invoiceNumber);

        return stockEntryRepository.saveAndFlush(stockEntry);
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal findLatestUnitCost(UUID businessId, UUID itemId) {
        return stockEntryRepository
                .findFirstByBusiness_IdAndProduct_IdOrderByCreatedDateDescIdDesc(businessId, itemId)
                .map(StockEntry::getUnitCost)
                .orElse(BigDecimal.ZERO);
    }

    @Override
    @Transactional(readOnly = true)
    public List<StockEntryResponse> findAllStockEntries(
            UUID businessId,
            UUID productId,
            StockEntryType entryType,
            String referenceType,
            UUID referenceId,
            LocalDateTime from,
            LocalDateTime to
    ) {
        businessHelper.findOwnedBusiness(businessId);
        if (productId != null) {
            findProduct(productId, businessId);
        }

        Specification<StockEntry> specification = stockEntrySpecification(
                businessId,
                productId,
                entryType,
                TextHelper.trimToNull(referenceType),
                referenceId,
                from,
                to
        );

        return stockEntryRepository.findAll(
                        specification,
                        Sort.by(Sort.Direction.DESC, "createdDate", "id")
                )
                .stream()
                .map(stockEntryMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public StockEntryResponse findStockEntryById(UUID businessId, UUID stockEntryId) {
        businessHelper.findOwnedBusiness(businessId);
        return stockEntryMapper.toResponse(findStockEntry(stockEntryId, businessId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<StockEntryResponse> findProductStockEntries(UUID businessId, UUID productId) {
        businessHelper.findOwnedBusiness(businessId);
        findProduct(productId, businessId);

        return stockEntryRepository.findAllByBusiness_IdAndProduct_IdOrderByCreatedDateDescIdDesc(businessId, productId)
                .stream()
                .map(stockEntryMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<StockSummaryResponse> findCurrentStock(UUID businessId) {
        businessHelper.findOwnedBusiness(businessId);
        Map<UUID, StockSummaryResponse> currentStockByProductId = new LinkedHashMap<>();

        stockEntryRepository.findAllByBusiness_IdOrderByCreatedDateDescIdDesc(businessId)
                .forEach(stockEntry -> currentStockByProductId.computeIfAbsent(
                        stockEntry.getProduct().getId(),
                        ignored -> stockEntryMapper.toSummary(stockEntry)
                ));

        return List.copyOf(currentStockByProductId.values());
    }

    @Override
    @Transactional(readOnly = true)
    public StockSummaryResponse findCurrentStockByProduct(UUID businessId, UUID productId) {
        businessHelper.findOwnedBusiness(businessId);
        findProduct(productId, businessId);

        return stockEntryRepository.findFirstByBusiness_IdAndProduct_IdOrderByCreatedDateDescIdDesc(businessId, productId)
                .map(stockEntryMapper::toSummary)
                .orElseGet(() -> stockEntryMapper.emptySummary(productId));
    }

    private Item findProduct(UUID productId, UUID businessId) {
        return itemRepository.findByIdAndBusinessId(productId, businessId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product has not been found"));
    }

    private StockEntry findStockEntry(UUID stockEntryId, UUID businessId) {
        return stockEntryRepository.findByIdAndBusiness_Id(stockEntryId, businessId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Stock entry has not been found"));
    }

    private BigDecimal normalizeQuantity(BigDecimal quantity) {
        return quantity.setScale(3, RoundingMode.HALF_UP);
    }

    private BigDecimal normalizeUnitCost(BigDecimal unitCost) {
        if (unitCost == null) {
            return null;
        }
        if (unitCost.compareTo(BigDecimal.ZERO) < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unit cost must be at least zero");
        }
        return unitCost.setScale(2, RoundingMode.HALF_UP);
    }

    private void validateQuantityChange(
            StockEntryType entryType,
            BigDecimal quantityChange,
            boolean productHasStockEntries
    ) {
        if (quantityChange.compareTo(BigDecimal.ZERO) == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Quantity change cannot be zero");
        }

        switch (entryType) {
            case OPENING_STOCK -> {
                if (productHasStockEntries) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Opening stock already exists for this product");
                }
                if (quantityChange.compareTo(BigDecimal.ZERO) < 0) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Opening stock must be at least zero");
                }
            }
            case STOCK_IN, RETURN -> requirePositiveQuantity(quantityChange, entryType);
            case STOCK_OUT, SALE -> requireNegativeQuantity(quantityChange, entryType);
            case ADJUSTMENT -> {
            }
        }
    }

    private void requirePositiveQuantity(BigDecimal quantityChange, StockEntryType entryType) {
        if (quantityChange.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, entryType + " quantity change must be positive");
        }
    }

    private void requireNegativeQuantity(BigDecimal quantityChange, StockEntryType entryType) {
        if (quantityChange.compareTo(BigDecimal.ZERO) >= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, entryType + " quantity change must be negative");
        }
    }

    private Specification<StockEntry> stockEntrySpecification(
            UUID businessId,
            UUID productId,
            StockEntryType entryType,
            String referenceType,
            UUID referenceId,
            LocalDateTime from,
            LocalDateTime to
    ) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(criteriaBuilder.equal(root.get("business").get("id"), businessId));

            if (productId != null) {
                predicates.add(criteriaBuilder.equal(root.get("product").get("id"), productId));
            }
            if (entryType != null) {
                predicates.add(criteriaBuilder.equal(root.get("entryType"), entryType));
            }
            if (referenceType != null) {
                predicates.add(criteriaBuilder.equal(
                        criteriaBuilder.lower(root.get("referenceType")),
                        referenceType.toLowerCase()
                ));
            }
            if (referenceId != null) {
                predicates.add(criteriaBuilder.equal(root.get("referenceId"), referenceId));
            }
            if (from != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("createdDate"), from));
            }
            if (to != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("createdDate"), to));
            }

            return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
        };
    }
}
