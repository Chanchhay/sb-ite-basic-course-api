package kh.edu.istad.ite.features.dataimport.validation;

import kh.edu.istad.ite.shared.enums.ImportTargetType;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** The validator for each kind of import, wired once at start-up. */
@Component
public class ImportValidatorRegistry {

    private final Map<ImportTargetType, ImportRowValidator> validators =
            new EnumMap<>(ImportTargetType.class);

    public ImportValidatorRegistry(List<ImportRowValidator> discovered) {
        discovered.forEach(validator -> validators.put(validator.targetType(), validator));
    }

    public ImportRowValidator forTarget(ImportTargetType targetType) {
        ImportRowValidator validator = validators.get(targetType);

        if (validator == null) {
            throw new IllegalStateException("No validator registered for " + targetType);
        }

        return validator;
    }
}
