package com.taskdle.model;

import java.util.ArrayList;
import java.util.List;

public class Grupo {
    private int id;
    private String nombre;
    private String propietario;
    private final List<String> miembros = new ArrayList<>();

    public Grupo(int id, String nombre, String propietario) {
        this.id = id;
        this.nombre = nombre;
        this.propietario = propietario;
    }

    public int getId() { return id; }
    public String getNombre() { return nombre; }
    public String getPropietario() { return propietario; }
    public List<String> getMiembros() { return miembros; }
}
