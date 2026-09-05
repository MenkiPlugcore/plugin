package store.moonsign.menu.util;

public final class Colors {
    private Colors() {}

    public static String legacy(String value) {
        return value == null ? "" : value.replace('&', '§');
    }
}
