package id.cadera.menkiestesparty;

/**
 * Small Bukkit-free layout helper for the v2.0.1 GUI visual theme.
 *
 * It intentionally only decides decorative tone. Functional slot placement
 * remains owned by the existing GUI managers.
 */
public final class GuiThemeLayout {
    public enum Tone { PRIMARY, ACCENT, SECONDARY }

    private GuiThemeLayout() {}

    public static Tone tone(int size, int slot) {
        if (size < 9 || size % 9 != 0 || slot < 0 || slot >= size) return Tone.PRIMARY;

        int rows = size / 9;
        int row = slot / 9;
        int column = slot % 9;
        boolean topOrBottom = row == 0 || row == rows - 1;

        if (topOrBottom && (column == 0 || column == 8)) return Tone.SECONDARY;
        if (topOrBottom && (column == 1 || column == 4 || column == 7)) return Tone.ACCENT;
        if (!topOrBottom && (column == 0 || column == 8) && row % 2 == 0) return Tone.SECONDARY;
        return Tone.PRIMARY;
    }
}
