package com.taskdle.model;

import java.time.Instant;

public class ActividadTarea {
    private final long id;
    private final String actor;
    private final String accion;
    private final String detalle;
    private final Instant fecha;

    public ActividadTarea(long id, String actor, String accion, String detalle, Instant fecha) {
        this.id = id;
        this.actor = actor;
        this.accion = accion;
        this.detalle = detalle;
        this.fecha = fecha;
    }

    public long getId() { return id; }
    public String getActor() { return actor; }
    public String getAccion() { return accion; }
    public String getDetalle() { return detalle; }
    public Instant getFecha() { return fecha; }
}
