package id.cadera.menkiestesparty.compat;

/** CI-only executable probe shipped harmlessly inside the production JAR. */
public final class RuntimeCompatibilityProbe {
    private RuntimeCompatibilityProbe() {}

    public static void main(String[] args) throws Exception {
        int javaFeature = Runtime.version().feature();
        if (javaFeature < 21) throw new IllegalStateException("Java 21+ required, got " + javaFeature);
        Class.forName("org.sqlite.JDBC");
        Class.forName("com.mysql.cj.jdbc.Driver");
        Class.forName("id.cadera.menkiestesparty.api.v2.MenkiPartyAPIv2");
        Class.forName("id.cadera.menkiestesparty.architecture.StorageDocumentSchema");
        Class.forName("id.cadera.menkiestesparty.architecture.NetworkEnvelope");
        System.out.println("MENKIESTESParty architecture-v2 runtime probe OK on Java " + javaFeature);
    }
}
