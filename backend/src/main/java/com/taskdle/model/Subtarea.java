package com.taskdle.model;

public class Subtarea {
    private int id;
    private String titulo;
    private boolean completada;
    private int orden;

    public Subtarea(String titulo) {
        this(0, titulo, false, 0);
    }

    public Subtarea(String titulo, boolean completada) {
        this(0, titulo, completada, 0);
    }

    public Subtarea(int id, String titulo, boolean completada, int orden) {
        this.id = id;
        this.titulo = titulo;
        this.completada = completada;
        this.orden = orden;
    }

    public int getId()                       { return id; }
    public void setId(int id)                { this.id = id; }
    public String getTitulo()                { return titulo; }
    public void setTitulo(String titulo)     { this.titulo = titulo; }
    public boolean isCompletada()            { return completada; }
    public void setCompletada(boolean c)     { this.completada = c; }
    public int getOrden()                    { return orden; }
    public void setOrden(int orden)          { this.orden = orden; }
    public void alternar()                   { this.completada = !this.completada; }
}
