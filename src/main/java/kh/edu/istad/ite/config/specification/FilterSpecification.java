package kh.edu.istad.ite.config.specification;

import jakarta.persistence.criteria.Predicate;
import kh.edu.istad.ite.config.filter.RequestDto;
import kh.edu.istad.ite.config.filter.SearchRequestDto;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.UUID;
import java.math.BigDecimal;

@Service
public class FilterSpecification<T> {

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Comparable parseValue(Class<?> javaType, String value) {
        if (value == null || value.isBlank()) return null;
        try {
            if (javaType.equals(String.class)) {
                return value;
            } else if (javaType.equals(LocalDateTime.class)) {
                try {
                    // Try parsing as ZonedDateTime first (handles 'Z' and '+07:00' etc)
                    return java.time.ZonedDateTime.parse(value).toLocalDateTime();
                } catch (DateTimeParseException e1) {
                    try {
                        // Fallback to standard LocalDateTime
                        if (value.contains(" ")) {
                            return LocalDateTime.parse(value.replace(" ", "T"));
                        }
                        return LocalDateTime.parse(value);
                    } catch (DateTimeParseException e2) {
                        // Fallback to LocalDate
                        return LocalDate.parse(value).atStartOfDay();
                    }
                }
            } else if (javaType.equals(LocalDate.class)) {
                if (value.contains("T")) {
                    return LocalDate.parse(value.substring(0, value.indexOf("T")));
                }
                return LocalDate.parse(value.trim());
            } else if (javaType.equals(UUID.class)) {
                return UUID.fromString(value.trim());
            } else if (javaType.equals(Long.class) || javaType.equals(long.class)) {
                return Long.parseLong(value.trim());
            } else if (javaType.equals(Integer.class) || javaType.equals(int.class)) {
                return Integer.parseInt(value.trim());
            } else if (javaType.equals(Double.class) || javaType.equals(double.class)) {
                return Double.parseDouble(value.trim());
            } else if (javaType.equals(BigDecimal.class)) {
                return new BigDecimal(value.trim());
            } else if (Enum.class.isAssignableFrom(javaType)) {
                return Enum.valueOf((Class<Enum>) javaType, value.trim());
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to parse filter value '" + value + "' for type " + javaType.getSimpleName(), e);
        }
        return value;
    }

    public Specification<T> getSearchSpecificationDynamic(List<SearchRequestDto> searchRequestDto,
                                                          RequestDto.GlobalOperator globalOperator) {

        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            for (SearchRequestDto requestDto : searchRequestDto) {
                switch (requestDto.getOperation()) {

                    case EQUAL:
                        Predicate equal = criteriaBuilder.equal(
                                root.get(requestDto.getColumn()),
                                parseValue(root.get(requestDto.getColumn()).getJavaType(), requestDto.getValue())
                        );
                        predicates.add(equal);
                        break;

                    case NOT_EQUAL:
                        Predicate notEqual = criteriaBuilder.notEqual(
                                root.get(requestDto.getColumn()),
                                parseValue(root.get(requestDto.getColumn()).getJavaType(), requestDto.getValue())
                        );
                        predicates.add(notEqual);
                        break;

                    case LIKE:
                        Predicate like = criteriaBuilder.like(
                                criteriaBuilder.lower(root.get(requestDto.getColumn())),
                                "%" + requestDto.getValue().toLowerCase() + "%"
                        );
                        predicates.add(like);
                        break;

                    case NOT_LIKE:
                        Predicate notLike = criteriaBuilder.notLike(
                                criteriaBuilder.lower(root.get(requestDto.getColumn())),
                                "%" + requestDto.getValue().toLowerCase() + "%"
                        );
                        predicates.add(notLike);
                        break;

                    case STARTS_WITH:
                        Predicate startsWith = criteriaBuilder.like(
                                criteriaBuilder.lower(root.get(requestDto.getColumn())),
                                requestDto.getValue().toLowerCase() + "%"
                        );
                        predicates.add(startsWith);
                        break;

                    case ENDS_WITH:
                        Predicate endsWith = criteriaBuilder.like(
                                criteriaBuilder.lower(root.get(requestDto.getColumn())),
                                "%" + requestDto.getValue().toLowerCase()
                        );
                        predicates.add(endsWith);
                        break;

                    case IN:
                        String[] inValues = requestDto.getValue().split(",");
                        Predicate in = root.get(requestDto.getColumn()).in(Arrays.asList(inValues));
                        predicates.add(in);
                        break;

                    case NOT_IN:
                        String[] notInValues = requestDto.getValue().split(",");
                        Predicate notIn = criteriaBuilder.not(
                                root.get(requestDto.getColumn()).in(Arrays.asList(notInValues))
                        );
                        predicates.add(notIn);
                        break;

                    case GREATER_THAN:
                        Predicate greaterThan = criteriaBuilder.greaterThan(
                                (jakarta.persistence.criteria.Expression) root.get(requestDto.getColumn()),
                                parseValue(root.get(requestDto.getColumn()).getJavaType(), requestDto.getValue())
                        );
                        predicates.add(greaterThan);
                        break;

                    case GREATER_THAN_EQUAL:
                        Predicate greaterThanEqual = criteriaBuilder.greaterThanOrEqualTo(
                                (jakarta.persistence.criteria.Expression) root.get(requestDto.getColumn()),
                                parseValue(root.get(requestDto.getColumn()).getJavaType(), requestDto.getValue())
                        );
                        predicates.add(greaterThanEqual);
                        break;

                    case LESS_THAN:
                        Predicate lessThan = criteriaBuilder.lessThan(
                                (jakarta.persistence.criteria.Expression) root.get(requestDto.getColumn()),
                                parseValue(root.get(requestDto.getColumn()).getJavaType(), requestDto.getValue())
                        );
                        predicates.add(lessThan);
                        break;

                    case LESS_THAN_EQUAL:
                        Predicate lessThanEqual = criteriaBuilder.lessThanOrEqualTo(
                                (jakarta.persistence.criteria.Expression) root.get(requestDto.getColumn()),
                                parseValue(root.get(requestDto.getColumn()).getJavaType(), requestDto.getValue())
                        );
                        predicates.add(lessThanEqual);
                        break;

                    case BETWEEN:
                        // Example value: "10,20"
                        String[] betweenValues = requestDto.getValue().split(",");

                        Predicate between = criteriaBuilder.between(
                                root.get(requestDto.getColumn()),
                                Long.parseLong(betweenValues[0].trim()),
                                Long.parseLong(betweenValues[1].trim())
                        );
                        predicates.add(between);
                        break;

                    case NOT_BETWEEN:
                        // Example value: "10,20"
                        String[] notBetweenValues = requestDto.getValue().split(",");

                        Predicate notBetween = criteriaBuilder.not(
                                criteriaBuilder.between(
                                        root.get(requestDto.getColumn()),
                                        Long.parseLong(notBetweenValues[0].trim()),
                                        Long.parseLong(notBetweenValues[1].trim())
                                )
                        );
                        predicates.add(notBetween);
                        break;

                    case IS_NULL:
                        Predicate isNull = criteriaBuilder.isNull(
                                root.get(requestDto.getColumn())
                        );
                        predicates.add(isNull);
                        break;

                    case IS_NOT_NULL:
                        Predicate isNotNull = criteriaBuilder.isNotNull(
                                root.get(requestDto.getColumn())
                        );
                        predicates.add(isNotNull);
                        break;

                    case TRUE:
                        Predicate isTrue = criteriaBuilder.isTrue(
                                root.get(requestDto.getColumn())
                        );
                        predicates.add(isTrue);
                        break;

                    case FALSE:
                        Predicate isFalse = criteriaBuilder.isFalse(
                                root.get(requestDto.getColumn())
                        );
                        predicates.add(isFalse);
                        break;

                    case JOIN:

                        Predicate join = criteriaBuilder.equal(
                                root.join(requestDto.getJoinTable()).get(requestDto.getColumn()),
                                parseValue(root.join(requestDto.getJoinTable()).get(requestDto.getColumn()).getJavaType(), requestDto.getValue())
                        );
                        predicates.add(join);
                        break;

                    default:
                        throw new IllegalStateException(
                                "Unexpected operation: " + requestDto.getOperation()
                        );
                }
            }

            if (globalOperator.equals(RequestDto.GlobalOperator.AND)) {
                return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
            } else return criteriaBuilder.or(predicates.toArray(new Predicate[0]));
        };
    }
}