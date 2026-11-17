package com.taskdle.db;

import java.sql.*;

/**
 * Gestión del pool de conexiones a PostgreSQL (Supabase).
 * La URL de conexión se lee de la variable de entorno DATABASE_URL.
 * Formato esperado: postgresql://user:password@host:port/dbname o postgres://...
 */
public class Database {

    private static final String ENV_VAR = "DATABASE_URL";
    private static String jdbcUrl;

    static {
        String raw = System.getenv(ENV_VAR);
        if (raw == null || raw.isBlank()) {
            System.err.println("[DB] ADVERTENCIA: " + ENV_VAR + " no definida.");
            jdbcUrl = null;
        } else {
            raw = raw.trim();
            // Normalizar postgres:// o postgresql:// hacia jdbc:postgresql://
            if (raw.startsWith("postgres://")) {
                raw = "jdbc:postgresql://" + raw.substring("postgres://".length());
            } else if (raw.startsWith("postgresql://")) {
                raw = "jdbc:postgresql://" + raw.substring("postgresql://".length());
            } else if (!raw.startsWith("jdbc:postgresql://")) {
                raw = "jdbc:postgresql://" + raw;
            }

            // Supabase requiere SSL en producción
            if (!raw.contains("sslmode")) {
                raw += (raw.contains("?") ? "&" : "?") + "sslmode=require";
            }
            jdbcUrl = raw;
        }

        try {
            Class.forName("org.postgresql.Driver");
        } catch (ClassNotFoundException e) {
            System.err.println("[DB] Driver PostgreSQL no encontrado.");
        }
    }

    public static boolean isAvailable() {
        return jdbcUrl != null;
    }

    public static Connection getConnection() throws SQLException {
        if (jdbcUrl == null) throw new SQLException("DATABASE_URL no configurada");
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
