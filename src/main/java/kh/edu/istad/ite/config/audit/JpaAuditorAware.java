package kh.edu.istad.ite.config.audit;

import org.jspecify.annotations.NonNull;
import org.springframework.data.domain.AuditorAware;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class JpaAuditorAware implements AuditorAware<String> {
    @Override
    public @NonNull Optional<String> getCurrentAuditor() {

//  Can customize the logic for security like getting the user's logged in uuid or username for the LastModifiedBy
//  or CreatedBy column

        return Optional.of("SYSTEM");
    }
}
