package kh.edu.istad.ite.features.dataimport.commit;

import kh.edu.istad.ite.shared.enums.ImportTargetType;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** The committer for each kind of import, wired once at start-up. */
@Component
public class ImportCommitterRegistry {

    private final Map<ImportTargetType, ImportCommitter> committers =
            new EnumMap<>(ImportTargetType.class);

    public ImportCommitterRegistry(List<ImportCommitter> discovered) {
        discovered.forEach(committer -> committers.put(committer.targetType(), committer));
    }

    public ImportCommitter forTarget(ImportTargetType targetType) {
        ImportCommitter committer = committers.get(targetType);

        if (committer == null) {
            throw new IllegalStateException("No committer registered for " + targetType);
        }

        return committer;
    }
}
