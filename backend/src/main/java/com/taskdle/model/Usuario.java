package com.taskdle.model;

import java.time.LocalDate;

/**
 * Value object que representa un usuario (sin lista de tareas en memoria).
 * Las tareas se cargan dinámicamente desde la BD por GestorUsuarios.
 */
public class Usuario {

    private final String nombreUsuario;
    private String passwordHash;
    private int rachaActual;
    private int mejorRacha;
    private LocalDate ultimaFechaCompletada;

    public Usuario(String nombreUsuario, String passwordHash) {
        this.nombreUsuario = nombreUsuario;
        this.passwordHash  = passwordHash;
        this.rachaActual   = 0;
        this.mejorRacha    = 0;
        this.ultimaFechaCompletada = null;
    }

    public Usuario(String nombreUsuario, String passwordHash,
                   int rachaActual, int mejorRacha, LocalDate ultimaFechaCompletada) {
        this.nombreUsuario = nombreUsuario;
        this.passwordHash  = passwordHash;
        this.rachaActual   = rachaActual;
        this.mejorRacha    = mejorRacha;
        this.ultimaFechaCompletada = ultimaFechaCompletada;
    }

    public boolean verificarPassword(String password) {
        // Simple comparación (en producción: BCrypt)
        return passwordHash != null && passwordHash.equals(password);
    }

    /** Registra la completación de una tarea y actualiza la racha. */
    public boolean registrarCompletacion() {
        LocalDate hoy = LocalDate.now();
        boolean cambio = false;

        if (ultimaFechaCompletada == null) {
            rachaActual = 1;
            cambio = true;
        } else {
            long dias = java.time.temporal.ChronoUnit.DAYS.between(ultimaFechaCompletada, hoy);
            if (dias == 0) {
                // Ya completó una tarea hoy: no cambia la racha
            } else if (dias == 1) {
                rachaActual++;
                cambio = true;
            } else {
                rachaActual = 1;
                cambio = true;
            }
        }

        ultimaFechaCompletada = hoy;
        if (rachaActual > mejorRacha) mejorRacha = rachaActual;
        return cambio;
    }

    public boolean rachaEnPeligro() {
        if (ultimaFechaCompletada == null || rachaActual == 0) return false;
        long dias = java.time.temporal.ChronoUnit.DAYS.between(ultimaFechaCompletada, LocalDate.now());
        return dias == 1;
    }

    public boolean getRachaViva() {
        if (ultimaFechaCompletada == null) return false;
        long dias = java.time.temporal.ChronoUnit.DAYS.between(ultimaFechaCompletada, LocalDate.now());
        return dias <= 1;
    }

    // Getters y Setters
    public String getNombreUsuario()                     { return nombreUsuario; }
    public String getPasswordHash()                      { return passwordHash; }
    public void setPasswordHash(String h)                { this.passwordHash = h; }
    public int getRachaActual()                          { return rachaActual; }
    public void setRachaActual(int r)                    { this.rachaActual = r; }
    public int getMejorRacha()                           { return mejorRacha; }
    public void setMejorRacha(int m)                     { this.mejorRacha = m; }
    public LocalDate getUltimaFechaCompletada()          { return ultimaFechaCompletada; }
    public void setUltimaFechaCompletada(LocalDate d)    { this.ultimaFechaCompletada = d; }
}
