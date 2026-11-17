package com.taskdle.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class TareaSimple extends Tarea {

    public TareaSimple(String titulo, String descripcion, Prioridad prioridad) {
        super(titulo, descripcion, prioridad);
    }

    public TareaSimple(int id, String titulo, String descripcion,
                       Prioridad prioridad, Estado estado, LocalDateTime fechaCreacion) {
        super(id, titulo, descripcion, prioridad, estado, fechaCreacion);
    }

    @Override public String getTipoTarea()    { return "SIMPLE"; }
    @Override public String getInfoAdicional(){ return "Tarea simple sin fecha límite"; }
    @Override public LocalDate getFechaLimite(){ return null; }
}
