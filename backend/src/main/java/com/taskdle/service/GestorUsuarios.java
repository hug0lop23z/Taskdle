package com.taskdle.service;

import com.taskdle.db.Database;
import com.taskdle.db.TareaDao;
import com.taskdle.db.UsuarioDao;
import com.taskdle.model.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Servicio principal con soporte dual:
 * - Si DATABASE_URL está configurada -> Usa PostgreSQL (Supabase)
 * - Si no está configurada o hay error -> Modo memoria automático (siempre funciona)
 */
public class GestorUsuarios {

    private final UsuarioDao usuarioDao = new UsuarioDao();
    private final TareaDao   tareaDao   = new TareaDao();

    // Almacenamiento en memoria resiliente (fallback)
    private static final Map<String, Usuario> usuariosMemoria = new ConcurrentHashMap<>();
    private static final Map<String, List<Tarea>> tareasMemoria = new ConcurrentHashMap<>();
    private static final AtomicInteger contadorId = new AtomicInteger(100);

    public GestorUsuarios() {
        if (Database.isAvailable()) {
            Database.initSchema();
        } else {
            System.out.println("  ℹ [GestorUsuarios] DATABASE_URL no detectada. Operando en memoria.");
        }
    }

    // ─── Usuarios ────────────────────────────────────────────────────────────

    /**
     * @return 0 = Éxito, 1 = Datos inválidos, 2 = Usuario ya existe
     */
    public int registrarUsuario(String nombre, String password) {
        if (nombre == null || nombre.isBlank() || nombre.length() < 3) return 1;
        if (password == null || password.length() < 4) return 1;

        if (Database.isAvailable()) {
            try {
                if (usuarioDao.existe(nombre)) return 2;
                boolean ok = usuarioDao.insertar(new Usuario(nombre, password));
                if (ok) {
                    usuariosMemoria.put(nombre, new Usuario(nombre, password));
                    return 0;
                }
            } catch (Exception e) {
                System.err.println("[GestorUsuarios] DB error, usando memoria: " + e.getMessage());
            }
        }

        // Modo memoria (siempre disponible)
        if (usuariosMemoria.containsKey(nombre)) return 2;
        usuariosMemoria.put(nombre, new Usuario(nombre, password));
        tareasMemoria.putIfAbsent(nombre, Collections.synchronizedList(new ArrayList<>()));
        return 0;
    }

    public Usuario autenticar(String nombre, String password) {
        if (Database.isAvailable()) {
            try {
                Usuario u = usuarioDao.buscar(nombre);
                if (u != null && u.verificarPassword(password)) return u;
            } catch (Exception ignored) {}
        }

        Usuario u = usuariosMemoria.get(nombre);
        if (u != null && u.verificarPassword(password)) return u;
        return null;
    }

    public Usuario getUsuario(String nombre) {
        if (Database.isAvailable()) {
            try {
                Usuario u = usuarioDao.buscar(nombre);
                if (u != null) return u;
            } catch (Exception ignored) {}
        }
        return usuariosMemoria.get(nombre);
    }

    // ─── Tareas ──────────────────────────────────────────────────────────────

    public List<Tarea> getTareas(String usuario) {
        if (Database.isAvailable()) {
            try {
                return tareaDao.listarPorUsuario(usuario);
            } catch (Exception ignored) {}
        }
        return tareasMemoria.getOrDefault(usuario, Collections.emptyList());
    }

    public int crearTarea(Tarea t, String usuario) {
        if (Database.isAvailable()) {
            try {
                int id = tareaDao.insertar(t, usuario);
                if (id > 0) return id;
            } catch (Exception ignored) {}
        }

        int id = contadorId.getAndIncrement();
        t.setId(id);
        for (int i = 0; i < t.getSubtareas().size(); i++) {
            t.getSubtareas().get(i).setId(i + 1);
        }
        tareasMemoria.computeIfAbsent(usuario, k -> Collections.synchronizedList(new ArrayList<>())).add(t);
        return id;
    }

    public boolean actualizarEstado(int id, String usuario, Estado nuevoEstado) {
        if (Database.isAvailable()) {
            try {
                boolean ok = tareaDao.actualizarEstado(id, usuario, nuevoEstado);
                if (ok) return true;
            } catch (Exception ignored) {}
        }

        List<Tarea> lista = tareasMemoria.get(usuario);
        if (lista != null) {
            for (Tarea t : lista) {
                if (t.getId() == id) {
                    t.setEstado(nuevoEstado);
                    return true;
                }
            }
        }
        return false;
    }

    public boolean eliminarTarea(int id, String usuario) {
        if (Database.isAvailable()) {
            try {
                boolean ok = tareaDao.eliminar(id, usuario);
                if (ok) return true;
            } catch (Exception ignored) {}
        }

        List<Tarea> lista = tareasMemoria.get(usuario);
        if (lista != null) {
            return lista.removeIf(t -> t.getId() == id);
        }
        return false;
    }

    public Tarea buscarTarea(int id, String usuario) {
        if (Database.isAvailable()) {
            try {
                Tarea t = tareaDao.buscarPorId(id, usuario);
                if (t != null) return t;
            } catch (Exception ignored) {}
        }

        List<Tarea> lista = tareasMemoria.get(usuario);
        if (lista != null) {
            for (Tarea t : lista) {
                if (t.getId() == id) return t;
            }
        }
        return null;
    }

    // ─── Racha ───────────────────────────────────────────────────────────────

    public boolean registrarCompletacion(String nombreUsuario) {
        Usuario u = getUsuario(nombreUsuario);
        if (u == null) return false;
        boolean cambio = u.registrarCompletacion();
        if (Database.isAvailable()) {
            try {
                usuarioDao.actualizarRacha(u);
            } catch (Exception ignored) {}
        }
        return cambio;
    }

    // ─── Subtareas ───────────────────────────────────────────────────────────

    public boolean actualizarSubtarea(int subtareaId, boolean completada) {
        if (Database.isAvailable()) {
            try {
                boolean ok = tareaDao.actualizarSubtarea(subtareaId, completada);
                if (ok) return true;
            } catch (Exception ignored) {}
        }

        for (List<Tarea> lista : tareasMemoria.values()) {
            for (Tarea t : lista) {
                for (Subtarea s : t.getSubtareas()) {
                    if (s.getId() == subtareaId) {
                        s.setCompletada(completada);
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
