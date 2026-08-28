package kh.edu.istad.ite.features.dataimport.service;

/** Running totals for one pass over a file, checking or committing. */
public final class ImportTotals {

    int total;
    int valid;
    int invalid;
    int duplicate;
    int openingStock;
    int entities;
    int created;
    int updated;
    int skipped;
    int failed;
    int itemGroups;
    int stockEntries;

    public int total() {
        return total;
    }

    public int valid() {
        return valid;
    }

    public int invalid() {
        return invalid;
    }

    public int duplicate() {
        return duplicate;
    }

    public int openingStock() {
        return openingStock;
    }

    /**
     * How many things would be created, counting a group of option rows as the
     * one item they describe.
     */
    public int entities() {
        return entities;
    }

    /** Units the file declared and this import had to create. */
    private int unitsCreated;

    public void addUnitsCreated(int count) {
        unitsCreated += count;
    }

    public int unitsCreated() {
        return unitsCreated;
    }

    public int created() {
        return created;
    }

    public int updated() {
        return updated;
    }

    public int skipped() {
        return skipped;
    }

    public int failed() {
        return failed;
    }

    public int itemGroups() {
        return itemGroups;
    }

    public int stockEntries() {
        return stockEntries;
    }
}
