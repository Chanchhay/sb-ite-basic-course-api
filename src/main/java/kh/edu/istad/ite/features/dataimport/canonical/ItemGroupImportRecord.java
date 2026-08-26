package kh.edu.istad.ite.features.dataimport.canonical;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A category, as read from one row.
 *
 * @param parentName the category this one sits under, by name; resolved
 *                   against existing categories at checking time, because a
 *                   parent may itself be created earlier in the same file
 */
public record ItemGroupImportRecord(
        String name,
        String note,
        String parentName
) implements ImportRecord {

    @Override
    public Map<String, Object> normalized() {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("name", name);
        values.put("note", note);
        values.put("parentName", parentName);
        return values;
    }

    @Override
    public String externalId() {
        return name;
    }
}
