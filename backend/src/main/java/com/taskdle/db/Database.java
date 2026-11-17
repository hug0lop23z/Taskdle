package com.taskdle.db;

import java.sql.*;
import java.util.Properties;

/**
 * Gestión del pool de conexiones a PostgreSQL (Supabase).
 * La URL de conexión se lee de la variable de entorno DATABASE_URL.
 * Formato esperado: postgresql://user:password@host:port/dbname
 */
public class Database {

    private static final String ENV_VAR = "DATABASE_URL";
    private static String jdbcUrl;

    static {
        String raw = System.getenv(ENV_VAR);
        if (raw == null || raw.isBlank()) {
            System.err.println("[DB] ADVERTENCIA: " + ENV_VAR + " no definida. Usando memoria temporal.");
            jdbcUrl = null;
        } else {
            // Supabase entrega: postgresql://user:pass@host:port/db  → convertir a jdbc:postgresql://
            jdbcUrl = raw.startsWith("jdbc:") ? raw : "jdbc:" + raw;
            // Supabase requiere SSL en producción
            if (!jdbcUrl.contains("sslmode")) {
                jdbcUrl += (jdbcUrl.contains("?") ? "&" : "?") + "sslmode=require";
            }
        }
        // Cargar driver JDBC
        try { Class.forName("org.postgresql.Driver"); }
        catch (ClassNotFoundException e) { System.err.println("[DB] Driver PostgreSQL no encontrado."); }
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
            // Leer schema.sql desde resources
            try (var is = Database.class.getResourceAsStream("/schema.sql")) {
                if (is == null) { System.err.println("[DB] schema.sql no encontrado"); return; }
                String sql = new String(is.readAllBytes());
                // Ejecutar cada sentencia separada por ;
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
