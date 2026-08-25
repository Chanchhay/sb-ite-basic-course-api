package kh.edu.istad.ite.shared.helper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public final class CambodiaProvinceMatcher {

    private static final Pattern PREFIX_PATTERN =
            Pattern.compile("^(khett?|krong)\\s+", Pattern.CASE_INSENSITIVE);
    private static final Pattern SUFFIX_PATTERN =
            Pattern.compile("\\s+province$", Pattern.CASE_INSENSITIVE);
    private static final Pattern KHMER_KHET_PREFIX = Pattern.compile("^ខេត្ត\\s*");
    private static final Pattern KHMER_KRONG_PREFIX = Pattern.compile("^ក្រុង\\s*");

    /** English canonical name -> alternate spellings a geocoder or old free entry might use. */
    private static final Map<String, List<String>> PROVINCES = new LinkedHashMap<>();

    static {
        PROVINCES.put("Phnom Penh", List.of("ភ្នំពេញ", "រាជធានីភ្នំពេញ", "Krong Phnom Penh", "Phnom Penh City"));
        PROVINCES.put("Banteay Meanchey", List.of("បន្ទាយមានជ័យ"));
        PROVINCES.put("Battambang", List.of("បាត់ដំបង"));
        PROVINCES.put("Kampong Cham", List.of("កំពង់ចាម"));
        PROVINCES.put("Kampong Chhnang", List.of("កំពង់ឆ្នាំង"));
        PROVINCES.put("Kampong Speu", List.of("កំពង់ស្ពឺ"));
        PROVINCES.put("Kampong Thom", List.of("កំពង់ធំ"));
        PROVINCES.put("Kampot", List.of("កំពត"));
        PROVINCES.put("Kandal", List.of("កណ្តាល"));
        PROVINCES.put("Kep", List.of("កែប"));
        PROVINCES.put("Koh Kong", List.of("កោះកុង"));
        PROVINCES.put("Kratie", List.of("ក្រចេះ"));
        PROVINCES.put("Mondulkiri", List.of("មណ្ឌលគិរី"));
        PROVINCES.put("Oddar Meanchey", List.of("ឧត្តរមានជ័យ"));
        PROVINCES.put("Pailin", List.of("ប៉ៃលិន"));
        PROVINCES.put("Preah Vihear", List.of("ព្រះវិហារ"));
        PROVINCES.put("Prey Veng", List.of("ព្រៃវែង"));
        PROVINCES.put("Pursat", List.of("ពោធិ៍សាត់"));
        PROVINCES.put("Ratanakiri", List.of("រតនគិរី"));
        PROVINCES.put("Siem Reap", List.of("សៀមរាប"));
        PROVINCES.put("Preah Sihanouk", List.of("ព្រះសីហនុ", "Sihanoukville"));
        PROVINCES.put("Stung Treng", List.of("ស្ទឹងត្រែង"));
        PROVINCES.put("Svay Rieng", List.of("ស្វាយរៀង"));
        PROVINCES.put("Takeo", List.of("តាកែវ"));
        PROVINCES.put("Tboung Khmum", List.of("ត្បូងឃ្មុំ"));
    }

    private CambodiaProvinceMatcher() {
    }

    private static String normalize(String text) {
        String result = text.trim().toLowerCase();
        result = PREFIX_PATTERN.matcher(result).replaceAll("");
        result = SUFFIX_PATTERN.matcher(result).replaceAll("");
        result = KHMER_KHET_PREFIX.matcher(result).replaceAll("");
        result = KHMER_KRONG_PREFIX.matcher(result).replaceAll("");
        return result.replaceAll("\\s+", " ").trim();
    }

    public static String match(String rawText) {
        if (rawText == null || rawText.isBlank()) {
            return null;
        }

        String target = normalize(rawText);

        for (Map.Entry<String, List<String>> entry : PROVINCES.entrySet()) {
            if (normalize(entry.getKey()).equals(target)) {
                return entry.getKey();
            }
            for (String alias : entry.getValue()) {
                if (normalize(alias).equals(target)) {
                    return entry.getKey();
                }
            }
        }

        for (Map.Entry<String, List<String>> entry : PROVINCES.entrySet()) {
            String normalizedName = normalize(entry.getKey());
            if (target.contains(normalizedName) || normalizedName.contains(target)) {
                return entry.getKey();
            }
            for (String alias : entry.getValue()) {
                String normalizedAlias = normalize(alias);
                if (target.contains(normalizedAlias) || normalizedAlias.contains(target)) {
                    return entry.getKey();
                }
            }
        }

        return null;
    }
}
