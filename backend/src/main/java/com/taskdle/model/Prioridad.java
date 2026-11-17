package com.taskdle.model;

public enum Prioridad {
    ALTA, MEDIA, BAJA;

    public static Prioridad fromInt(int opcion) {
        return switch (opcion) {
            case 1 -> ALTA;
            case 2 -> MEDIA;
            case 3 -> BAJA;
            default -> MEDIA;
        };
    }

    public static Prioridad fromString(String s) {
        if (s == null) return MEDIA;
        try { return valueOf(s.toUpperCase()); }
        catch (Exception e) { return MEDIA; }
    }
}
