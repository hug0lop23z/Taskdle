package com.taskdle.model;

public enum Estado {
    PENDIENTE, EN_PROGRESO, COMPLETADA;

    public static Estado fromString(String s) {
        if (s == null) return PENDIENTE;
        try { return valueOf(s.toUpperCase()); }
        catch (Exception e) { return PENDIENTE; }
    }
}
