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
    private final com.taskdle.db.GrupoDao grupoDao = new com.taskdle.db.GrupoDao();

    // Almacenamiento en memoria resiliente (fallback)
    private static final Map<String, Usuario> usuariosMemoria = new ConcurrentHashMap<>();
    private static final Map<String, List<Tarea>> tareasMemoria = new ConcurrentHashMap<>();
    private static final Map<Integer, com.taskdle.model.Grupo> gruposMemoria = new ConcurrentHashMap<>();
    private static final Map<Integer, String> propietariosTareasMemoria = new ConcurrentHashMap<>();
    private static final AtomicInteger contadorId = new AtomicInteger(100);
    private static final AtomicInteger contadorGrupo = new AtomicInteger(1);

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
        if (nombre == null) return null;
        if (Database.isAvailable()) {
            try {
                Usuario u = usuarioDao.buscar(nombre);
                if (u != null) return u;
            } catch (Exception ignored) {}
        }
        for (Map.Entry<String, Usuario> entry : usuariosMemoria.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(nombre.trim())) {
                return entry.getValue();
            }
        }
        return null;
    }

    // ─── Grupos ──────────────────────────────────────────────────────────────

    public List<com.taskdle.model.Grupo> getGrupos(String usuario) {
        if (Database.isAvailable()) return grupoDao.listar(usuario);
        List<com.taskdle.model.Grupo> grupos = new ArrayList<>();
        for (com.taskdle.model.Grupo grupo : gruposMemoria.values()) {
            if (usuario.equalsIgnoreCase(grupo.getPropietario()) || grupo.getMiembros().contains(usuario)) {
                grupos.add(grupo);
            }
        }
        return grupos;
    }

    public int crearGrupo(String propietario, String nombre) {
        if (propietario == null || nombre == null || nombre.trim().isEmpty()) return -1;
        if (Database.isAvailable()) return grupoDao.crear(propietario, nombre.trim());
        int id = contadorGrupo.getAndIncrement();
        gruposMemoria.put(id, new com.taskdle.model.Grupo(id, nombre.trim(), propietario));
        return id;
    }

    public boolean agregarMiembroGrupo(int grupoId, String propietario, String miembro) {
        Usuario usuario = getUsuario(miembro);
        if (usuario == null) return false;
        if (Database.isAvailable()) {
            if (!grupoDao.esPropietario(grupoId, propietario)) return false;
            return grupoDao.agregarMiembro(grupoId, usuario.getNombreUsuario());
        }
        com.taskdle.model.Grupo grupo = gruposMemoria.get(grupoId);
        if (grupo == null || !propietario.equalsIgnoreCase(grupo.getPropietario())) return false;
        if (!grupo.getMiembros().contains(usuario.getNombreUsuario())) grupo.getMiembros().add(usuario.getNombreUsuario());
        return true;
    }

    public boolean quitarMiembroGrupo(int grupoId, String propietario, String miembro) {
        if (Database.isAvailable()) {
            if (!grupoDao.esPropietario(grupoId, propietario)) return false;
            return grupoDao.quitarMiembro(grupoId, miembro);
        }
        com.taskdle.model.Grupo grupo = gruposMemoria.get(grupoId);
        if (grupo == null || !propietario.equalsIgnoreCase(grupo.getPropietario())) return false;
        return grupo.getMiembros().remove(miembro);
    }

    public boolean eliminarGrupo(int grupoId, String propietario) {
        if (Database.isAvailable()) return grupoDao.eliminar(grupoId, propietario);
        com.taskdle.model.Grupo grupo = gruposMemoria.get(grupoId);
        if (grupo == null || !propietario.equalsIgnoreCase(grupo.getPropietario())) return false;
        gruposMemoria.remove(grupoId);
        return true;
    }

    public boolean compartirTareaGrupo(int grupoId, int tareaId, String propietario) {
        if (Database.isAvailable()) return grupoDao.compartirTarea(grupoId, tareaId, propietario);
        com.taskdle.model.Grupo grupo = gruposMemoria.get(grupoId);
        if (grupo == null || !propietario.equalsIgnoreCase(grupo.getPropietario())) return false;
        Tarea tarea = buscarTarea(tareaId, propietario);
        if (tarea == null) return false;
        for (String miembro : grupo.getMiembros()) {
            if (miembro.equalsIgnoreCase(propietario)) continue;
            List<Tarea> lista = tareasMemoria.computeIfAbsent(miembro, k -> Collections.synchronizedList(new ArrayList<>()));
            if (lista.stream().noneMatch(t -> t.getId() == tareaId)) lista.add(tarea);
        }
        return true;
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
        return crearTarea(t, usuario, null);
    }

    public int crearTarea(Tarea t, String usuario, String compartirCon) {
        int id = -1;
        if (Database.isAvailable()) {
            try {
                id = tareaDao.insertar(t, usuario);
            } catch (Exception ignored) {}
        }

        if (id <= 0) {
            id = contadorId.getAndIncrement();
            t.setId(id);
            propietariosTareasMemoria.put(id, usuario);
            for (int i = 0; i < t.getSubtareas().size(); i++) {
                t.getSubtareas().get(i).setId(i + 1);
            }
            tareasMemoria.computeIfAbsent(usuario, k -> Collections.synchronizedList(new ArrayList<>())).add(t);
        }

        // Procesar usuarios para compartir
        if (compartirCon != null && !compartirCon.isBlank()) {
            String[] destinos = compartirCon.split(",");
            for (String dest : destinos) {
                String d = dest.trim();
                if (!d.isEmpty() && !d.equalsIgnoreCase(usuario)) {
                    compartirTarea(t.getId(), usuario, d);
                }
            }
        }

        return id;
    }

    public boolean compartirTarea(int id, String usuarioOrigen, String usuarioDestino) {
        if (usuarioDestino == null || usuarioDestino.isBlank()) return false;
        if (usuarioOrigen != null && usuarioOrigen.equalsIgnoreCase(usuarioDestino.trim())) return false;
        Usuario target = getUsuario(usuarioDestino.trim());
        if (target == null) return false;
        String nombreRealDestino = target.getNombreUsuario();

        boolean compartido = false;
        if (Database.isAvailable()) {
            try {
                compartido = tareaDao.compartir(id, nombreRealDestino);
            } catch (Exception ignored) {}
        }

        // Modo memoria
        Tarea t = buscarTarea(id, usuarioOrigen);
        if (t != null) {
            List<Tarea> listaDestino = tareasMemoria.computeIfAbsent(nombreRealDestino, k -> Collections.synchronizedList(new ArrayList<>()));
            boolean yaExiste = listaDestino.stream().anyMatch(item -> item.getId() == id);
            if (!yaExiste) {
                listaDestino.add(t);
            }
            compartido = true;
        }

        return compartido;
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

    public boolean actualizarFechaLimite(int id, String usuario, java.time.Instant fechaLimite) {
        if (fechaLimite == null) return false;
        if (Database.isAvailable()) return tareaDao.actualizarFechaLimite(id, usuario, fechaLimite);

        if (!usuario.equals(propietariosTareasMemoria.get(id))) return false;
        List<Tarea> lista = tareasMemoria.get(usuario);
        if (lista == null) return false;
        for (Tarea tarea : lista) {
            if (tarea.getId() == id && tarea instanceof TareaConFechaLimite conFecha) {
                conFecha.setFechaLimite(fechaLimite);
                return true;
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
