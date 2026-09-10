package id.cadera.menkiestesparty.compat;

/** CI-only executable probe shipped harmlessly inside the production JAR. */
public final class RuntimeCompatibilityProbe {
    private RuntimeCompatibilityProbe() {}

    public static void main(String[] args) throws Exception {
        int javaFeature = Runtime.version().feature();
        if (javaFeature < 21) throw new IllegalStateException("Java 21+ required, got " + javaFeature);
        Class.forName("org.sqlite.JDBC");
        Class.forName("com.mysql.cj.jdbc.Driver");
        System.out.println("MENKIESTESParty runtime probe OK on Java " + javaFeature);
    }
}
