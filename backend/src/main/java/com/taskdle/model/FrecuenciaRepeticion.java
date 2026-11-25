package com.taskdle.model;

import java.time.ZonedDateTime;

public enum FrecuenciaRepeticion {
    DIARIA,
    SEMANAL,
    MENSUAL;

    public ZonedDateTime ocurrenciaDesde(ZonedDateTime inicio, int indice) {
        return switch (this) {
            case DIARIA -> inicio.plusDays(indice);
            case SEMANAL -> inicio.plusWeeks(indice);
            case MENSUAL -> inicio.plusMonths(indice);
        };
    }

    public static FrecuenciaRepeticion fromString(String value) {
        if (value == null) throw new IllegalArgumentException("La frecuencia es obligatoria");
        return FrecuenciaRepeticion.valueOf(value.trim().toUpperCase());
    }
}
