package com.taskdle.model;

/**
 * Value object que representa un usuario (sin lista de tareas en memoria).
 * Las tareas se cargan dinámicamente desde la BD por GestorUsuarios.
 */
public class Usuario {

    private final String nombreUsuario;
    private String passwordHash;

    public Usuario(String nombreUsuario, String passwordHash) {
        this.nombreUsuario = nombreUsuario;
        this.passwordHash  = passwordHash;
    }

    public boolean verificarPassword(String password) {
        // Simple comparación (en producción: BCrypt)
        return passwordHash != null && passwordHash.equals(password);
    }

    // Getters y Setters
    public String getNombreUsuario()                     { return nombreUsuario; }
    public String getPasswordHash()                      { return passwordHash; }
    public void setPasswordHash(String h)                { this.passwordHash = h; }
}
