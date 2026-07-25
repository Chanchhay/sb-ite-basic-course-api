package kh.edu.istad.ite.features.business.dto;

import java.util.List;
import java.util.UUID;

public record StorefrontStatusResponse(
        UUID businessId,
        String slug,
        String storefrontUrl,
        boolean listed,
        boolean readyToPublish,
        List<StorefrontRequirement> requirements
) {

    public record StorefrontRequirement(
            String code,
            String label,
            boolean satisfied,
            boolean blocking
    ) {
    }
}
