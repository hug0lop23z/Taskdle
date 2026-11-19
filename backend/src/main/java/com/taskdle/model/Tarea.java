package com.taskdle.model;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public abstract class Tarea {

    private int id;
    private String titulo;
    private String descripcion;
    private Prioridad prioridad;
    private Estado estado;
    private LocalDateTime fechaCreacion;
    private List<Subtarea> subtareas;

    protected Tarea(String titulo, String descripcion, Prioridad prioridad) {
        this.titulo        = titulo;
        this.descripcion   = descripcion;
        this.prioridad     = prioridad;
        this.estado        = Estado.PENDIENTE;
        this.fechaCreacion = LocalDateTime.now();
        this.subtareas     = new ArrayList<>();
    }

    // Constructor para reconstrucción desde BD
    protected Tarea(int id, String titulo, String descripcion,
                    Prioridad prioridad, Estado estado, LocalDateTime fechaCreacion) {
        this.id            = id;
        this.titulo        = titulo;
        this.descripcion   = descripcion;
        this.prioridad     = prioridad;
        this.estado        = estado;
        this.fechaCreacion = fechaCreacion;
        this.subtareas     = new ArrayList<>();
    }

    public abstract String getTipoTarea();
    public abstract String getInfoAdicional();
    public abstract Instant getFechaLimite();

    public boolean estaCompletada() { return estado == Estado.COMPLETADA; }
    public void completar()         { this.estado = Estado.COMPLETADA; }

    public void agregarSubtarea(Subtarea s)    { subtareas.add(s); }
    public List<Subtarea> getSubtareas()       { return subtareas; }

    public int getId()                         { return id; }
    public void setId(int id)                  { this.id = id; }
    public String getTitulo()                  { return titulo; }
    public void setTitulo(String t)            { this.titulo = t; }
    public String getDescripcion()             { return descripcion; }
    public void setDescripcion(String d)       { this.descripcion = d; }
    public Prioridad getPrioridad()            { return prioridad; }
    public void setPrioridad(Prioridad p)      { this.prioridad = p; }
    public Estado getEstado()                  { return estado; }
    public void setEstado(Estado e)            { this.estado = e; }
    public LocalDateTime getFechaCreacion()    { return fechaCreacion; }
    public void setFechaCreacion(LocalDateTime f) { this.fechaCreacion = f; }
}
