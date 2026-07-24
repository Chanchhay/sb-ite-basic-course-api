package kh.edu.istad.ite.shared.helper;

import org.springframework.util.StringUtils;

import java.text.Normalizer;
import java.util.Locale;
import java.util.function.Predicate;

public final class SlugHelper {

    private SlugHelper() {
    }

    public static String toSlugBase(String value, String fallback, int maxLength) {
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");

        if (!StringUtils.hasText(normalized)) {
            return fallback;
        }

        return truncateTrailingDash(normalized, maxLength);
    }

    public static String generateUniqueSlug(
            String value,
            String fallback,
            int maxLength,
            Predicate<String> slugExists
    ) {
        String baseSlug = toSlugBase(value, fallback, maxLength);
        String candidate = baseSlug;
        int suffix = 1;

        while (slugExists.test(candidate)) {
            String suffixText = "-" + suffix;
            int baseMaxLength = maxLength - suffixText.length();
            candidate = truncateTrailingDash(baseSlug, baseMaxLength) + suffixText;
            suffix++;
        }

        return candidate;
    }

    private static String truncateTrailingDash(String value, int maxLength) {
        if (maxLength < 1) {
            return "";
        }

        String truncated = value.length() > maxLength ? value.substring(0, maxLength) : value;
        return truncated.replaceAll("-$", "");
    }
}
