package com.softtek.mcp;

import java.io.InputStream;
import java.util.Properties;

public final class VersionLoader {
    private static final String UNKNOWN = "unknown";
    private static String version;

    public static synchronized String getVersion() {
        if (version != null) return version;
        try (InputStream in = VersionLoader.class.getResourceAsStream("/version.properties")) {
            if (in != null) {
                Properties props = new Properties();
                props.load(in);
                version = props.getProperty("app.version", UNKNOWN);
            } else {
                version = UNKNOWN;
            }
        } catch (Exception e) {
            version = UNKNOWN;
        }
        return version;
    }

    private VersionLoader() {}
}
