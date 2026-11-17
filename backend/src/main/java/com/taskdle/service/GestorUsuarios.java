package com.taskdle.service;

import com.taskdle.db.Database;
import com.taskdle.db.TareaDao;
import com.taskdle.db.UsuarioDao;
import com.taskdle.model.*;
import java.util.List;

/**
 * Servicio principal. Encapsula la lógica de negocio y delega en los DAOs.
 * Opera directamente contra PostgreSQL; NO mantiene estado en memoria.
 */
public class GestorUsuarios {

    private final UsuarioDao usuarioDao = new UsuarioDao();
    private final TareaDao   tareaDao   = new TareaDao();

    public GestorUsuarios() {
        Database.initSchema();
    }

    // ─── Usuarios ────────────────────────────────────────────────────────────

    /**
     * @return 0 = Éxito, 1 = Datos inválidos, 2 = Usuario ya existe, 3 = Error de Base de Datos
     */
    public int registrarUsuario(String nombre, String password) {
        if (nombre == null || nombre.isBlank() || nombre.length() < 3) return 1;
        if (password == null || password.length() < 4) return 1;
        if (!Database.isAvailable()) return 3;
        try {
            if (usuarioDao.existe(nombre)) return 2;
            boolean ok = usuarioDao.insertar(new Usuario(nombre, password));
            return ok ? 0 : 3;
        } catch (Exception e) {
            System.err.println("[GestorUsuarios] Error registrando usuario: " + e.getMessage());
            return 3;
        }
    }

    public Usuario autenticar(String nombre, String password) {
        if (!Database.isAvailable()) return null;
        Usuario u = usuarioDao.buscar(nombre);
        if (u != null && u.verificarPassword(password)) return u;
        return null;
    }

    public Usuario getUsuario(String nombre) {
        if (!Database.isAvailable()) return null;
        return usuarioDao.buscar(nombre);
    }

    // ─── Tareas ──────────────────────────────────────────────────────────────

    public List<Tarea> getTareas(String usuario) {
        return tareaDao.listarPorUsuario(usuario);
    }

    public int crearTarea(Tarea t, String usuario) {
        return tareaDao.insertar(t, usuario);
    }

    public boolean actualizarEstado(int id, String usuario, Estado nuevoEstado) {
        return tareaDao.actualizarEstado(id, usuario, nuevoEstado);
    }

    public boolean eliminarTarea(int id, String usuario) {
        return tareaDao.eliminar(id, usuario);
    }

    public Tarea buscarTarea(int id, String usuario) {
        return tareaDao.buscarPorId(id, usuario);
    }

    // ─── Racha (con persistencia) ─────────────────────────────────────────────

    public boolean registrarCompletacion(String nombreUsuario) {
        Usuario u = usuarioDao.buscar(nombreUsuario);
        if (u == null) return false;
        boolean cambio = u.registrarCompletacion();
        usuarioDao.actualizarRacha(u);
        return cambio;
    }

    // ─── Subtareas ───────────────────────────────────────────────────────────

    public boolean actualizarSubtarea(int subtareaId, boolean completada) {
        return tareaDao.actualizarSubtarea(subtareaId, completada);
    }
}
