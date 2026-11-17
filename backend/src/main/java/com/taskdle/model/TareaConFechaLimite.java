package com.taskdle.model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

public class TareaConFechaLimite extends Tarea {

    private LocalDate fechaLimite;

    public TareaConFechaLimite(String titulo, String descripcion,
                                Prioridad prioridad, LocalDate fechaLimite) {
        super(titulo, descripcion, prioridad);
        this.fechaLimite = fechaLimite;
    }

    public TareaConFechaLimite(int id, String titulo, String descripcion,
                                Prioridad prioridad, Estado estado,
                                LocalDateTime fechaCreacion, LocalDate fechaLimite) {
        super(id, titulo, descripcion, prioridad, estado, fechaCreacion);
        this.fechaLimite = fechaLimite;
    }

    public boolean estaVencida() {
        return !estaCompletada() && LocalDate.now().isAfter(fechaLimite);
    }

    public long getDiasRestantes() {
        return ChronoUnit.DAYS.between(LocalDate.now(), fechaLimite);
    }

    @Override public LocalDate getFechaLimite() { return fechaLimite; }
    public void setFechaLimite(LocalDate f)     { this.fechaLimite = f; }

    @Override public String getTipoTarea() { return "FECHA"; }

    @Override
    public String getInfoAdicional() {
        if (estaCompletada()) return "Completada a tiempo";
        if (estaVencida()) return "VENCIDA (hace " + Math.abs(getDiasRestantes()) + " días)";
        long dias = getDiasRestantes();
        if (dias == 0) return "¡Vence HOY!";
        if (dias == 1) return "Vence MAÑANA";
        return "Quedan " + dias + " días";
    }
}
