package com.taskdle.db;

import java.net.URI;
import java.sql.*;
import java.util.Properties;

/**
 * Gestión de conexiones a PostgreSQL (Supabase).
 * Soporta URI de Supabase y analiza usuario, contraseña y host de forma robusta.
 */
public class Database {

    private static String jdbcUrl;
    private static final Properties connectionProps = new Properties();

    static {
        init();
    }

    private static String getDbUrlEnv() {
        String[] vars = {"DATABASE_URL", "DATABASE_URI", "DB_URL", "POSTGRES_URL", "POSTGRESQL_URL", "SUPABASE_DATABASE_URL"};
        for (String v : vars) {
            String val = System.getenv(v);
            if (val != null && !val.isBlank()) {
                System.out.println("  ℹ [DB] Detectada variable de entorno: " + v);
                return val;
            }
        }
        return null;
    }

    public static synchronized void init() {
        String raw = getDbUrlEnv();
        if (raw == null || raw.isBlank()) {
            jdbcUrl = null;
            return;
        }

        try {
            Class.forName("org.postgresql.Driver");
        } catch (ClassNotFoundException e) {
            System.err.println("[DB] Driver PostgreSQL no encontrado.");
        }

        raw = raw.trim();
        try {
            String uriStr = raw;
            if (uriStr.startsWith("jdbc:postgresql://")) {
                uriStr = "postgresql://" + uriStr.substring("jdbc:postgresql://".length());
            } else if (uriStr.startsWith("jdbc:postgres://")) {
                uriStr = "postgresql://" + uriStr.substring("jdbc:postgres://".length());
            }

            URI uri = new URI(uriStr);
            String userInfo = uri.getUserInfo();
            String host = uri.getHost();
            int port = uri.getPort() > 0 ? uri.getPort() : 5432;
            String path = uri.getPath();
            if (path != null && path.startsWith("/")) path = path.substring(1);

            jdbcUrl = "jdbc:postgresql://" + host + ":" + port + "/" + (path != null && !path.isBlank() ? path : "postgres");
            connectionProps.clear();

            if (userInfo != null && userInfo.contains(":")) {
                String[] parts = userInfo.split(":", 2);
                connectionProps.setProperty("user", parts[0]);
                connectionProps.setProperty("password", parts[1]);
            }

            connectionProps.setProperty("sslmode", "require");
            System.out.println("  ✅ [DB] URL configurada hacia host: " + host + ":" + port);

        } catch (Exception e) {
            if (!raw.startsWith("jdbc:")) raw = "jdbc:" + raw;
            if (!raw.contains("sslmode")) raw += (raw.contains("?") ? "&" : "?") + "sslmode=require";
            jdbcUrl = raw;
            connectionProps.clear();
        }
    }

    public static boolean isAvailable() {
        if (jdbcUrl == null) init();
        return jdbcUrl != null;
    }

    public static Connection getConnection() throws SQLException {
        if (!isAvailable()) throw new SQLException("DATABASE_URL no configurada en las variables de entorno de Render");
        if (!connectionProps.isEmpty()) {
            return DriverManager.getConnection(jdbcUrl, connectionProps);
        }
        return DriverManager.getConnection(jdbcUrl);
    }

    /** Inicializa el esquema si las tablas no existen aún. */
    public static void initSchema() {
        if (!isAvailable()) return;
        try (Connection conn = getConnection();
             Statement st = conn.createStatement()) {
            try (var is = Database.class.getResourceAsStream("/schema.sql")) {
                if (is == null) {
                    System.err.println("[DB] schema.sql no encontrado");
                    return;
                }
                String sql = new String(is.readAllBytes());
                for (String stmt : sql.split(";")) {
                    String s = stmt.trim();
                    if (!s.isBlank()) st.execute(s);
                }
                System.out.println("  ✅ Esquema de BD inicializado correctamente.");
            }
        } catch (Exception e) {
            System.err.println("[DB] Error inicializando schema: " + e.getMessage());
        }
    }
}
