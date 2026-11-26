package com.taskdle.service;

import com.taskdle.db.Database;
import com.taskdle.db.TareaDao;
import com.taskdle.db.UsuarioDao;
import com.taskdle.model.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
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
    private static final Map<Integer, SerieMemoria> seriesMemoria = new ConcurrentHashMap<>();
    private static final AtomicInteger contadorId = new AtomicInteger(100);
    private static final AtomicInteger contadorGrupo = new AtomicInteger(1);
    private static final AtomicInteger contadorSerie = new AtomicInteger(1);

    private static final class SerieMemoria {
        private final int id;
        private final String propietario;
        private final FrecuenciaRepeticion frecuencia;
        private ZonedDateTime inicio;
        private boolean activa = true;

        private SerieMemoria(int id, String propietario, FrecuenciaRepeticion frecuencia, ZonedDateTime inicio) {
            this.id = id;
            this.propietario = propietario;
            this.frecuencia = frecuencia;
            this.inicio = inicio;
        }
    }

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
        Integer serieId = tarea.getSerieRecurrenteId();
        List<Tarea> tareasCompartir = serieId == null
            ? List.of(tarea)
            : tareasMemoria.getOrDefault(propietario, Collections.emptyList()).stream()
                .filter(item -> serieId.equals(item.getSerieRecurrenteId()))
                .toList();
        for (String miembro : grupo.getMiembros()) {
            if (miembro.equalsIgnoreCase(propietario)) continue;
            List<Tarea> lista = tareasMemoria.computeIfAbsent(miembro, k -> Collections.synchronizedList(new ArrayList<>()));
            for (Tarea ocurrencia : tareasCompartir) {
                if (lista.stream().noneMatch(item -> item.getId() == ocurrencia.getId())) lista.add(ocurrencia);
            }
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
            t.setPropietario(usuario);
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

    public List<Integer> crearSerieRecurrente(Tarea tarea, String usuario, String compartirCon,
                                              FrecuenciaRepeticion frecuencia, ZoneId zonaHoraria,
                                              int cantidadOcurrencias) {
        boolean baseDisponible = Database.isAvailable();
        List<Integer> ids = baseDisponible
            ? tareaDao.insertarSerie(tarea, usuario, frecuencia, zonaHoraria, cantidadOcurrencias)
            : Collections.emptyList();

        if (ids.isEmpty()) {
            if (baseDisponible) return Collections.emptyList();
            if (!(tarea instanceof TareaConFechaLimite conFecha)) return Collections.emptyList();
            int serieId = contadorSerie.getAndIncrement();
            SerieMemoria serie = new SerieMemoria(serieId, usuario, frecuencia, conFecha.getFechaLimite().atZone(zonaHoraria));
            seriesMemoria.put(serieId, serie);
            ids = new ArrayList<>();
            for (int indice = 0; indice < cantidadOcurrencias; indice++) {
                Instant fecha = frecuencia.ocurrenciaDesde(serie.inicio, indice).toInstant();
                TareaConFechaLimite ocurrencia = new TareaConFechaLimite(
                    tarea.getTitulo(), tarea.getDescripcion(), tarea.getPrioridad(), fecha);
                for (Subtarea subtarea : tarea.getSubtareas()) {
                    ocurrencia.agregarSubtarea(new Subtarea(subtarea.getTitulo()));
                }
                prepararOcurrenciaMemoria(ocurrencia, serie, indice, usuario);
                ids.add(ocurrencia.getId());
            }
        }

        if (compartirCon != null && !compartirCon.isBlank()) {
            for (int id : ids) {
                for (String destino : compartirCon.split(",")) {
                    if (!destino.isBlank()) compartirTarea(id, usuario, destino.trim());
                }
            }
        }
        return ids;
    }

    public int asegurarOcurrenciasFuturas(int tareaId, String usuario, int cantidadOcurrencias) {
        if (Database.isAvailable()) {
            return tareaDao.asegurarOcurrenciasFuturas(tareaId, usuario, cantidadOcurrencias);
        }

        Tarea tarea = buscarTarea(tareaId, usuario);
        if (tarea == null || tarea.getSerieRecurrenteId() == null || !tarea.isRepeticionActiva()) return 0;
        SerieMemoria serie = seriesMemoria.get(tarea.getSerieRecurrenteId());
        if (serie == null || !serie.activa) return 0;

        List<Tarea> propias = tareasMemoria.get(serie.propietario);
        int pendientes = (int) propias.stream()
            .filter(item -> Integer.valueOf(serie.id).equals(item.getSerieRecurrenteId()))
            .filter(item -> item.getEstado() != Estado.COMPLETADA)
            .count();
        int siguienteIndice = propias.stream()
            .filter(item -> Integer.valueOf(serie.id).equals(item.getSerieRecurrenteId()))
            .map(Tarea::getIndiceRecurrente)
            .filter(java.util.Objects::nonNull)
            .mapToInt(Integer::intValue)
            .max().orElse(tarea.getIndiceRecurrente() == null ? -1 : tarea.getIndiceRecurrente()) + 1;
        int creadas = 0;
        while (pendientes < cantidadOcurrencias) {
            Instant fecha = serie.frecuencia.ocurrenciaDesde(serie.inicio, siguienteIndice).toInstant();
            TareaConFechaLimite nueva = new TareaConFechaLimite(
                tarea.getTitulo(), tarea.getDescripcion(), tarea.getPrioridad(), fecha);
            for (Subtarea subtarea : tarea.getSubtareas()) {
                nueva.agregarSubtarea(new Subtarea(subtarea.getTitulo()));
            }
            prepararOcurrenciaMemoria(nueva, serie, siguienteIndice, serie.propietario);
            for (Map.Entry<String, List<Tarea>> entry : tareasMemoria.entrySet()) {
                if (!entry.getKey().equals(serie.propietario)
                    && entry.getValue().stream().anyMatch(item -> item.getId() == tareaId)) {
                    entry.getValue().add(nueva);
                }
            }
            siguienteIndice++;
            pendientes++;
            creadas++;
        }
        return creadas;
    }

    public boolean detenerSerieRecurrente(int tareaId, String usuario) {
        if (Database.isAvailable()) return tareaDao.detenerSerie(tareaId, usuario);
        if (!usuario.equals(propietariosTareasMemoria.get(tareaId))) return false;
        Tarea actual = buscarTarea(tareaId, usuario);
        if (actual == null || actual.getSerieRecurrenteId() == null) return false;
        SerieMemoria serie = seriesMemoria.get(actual.getSerieRecurrenteId());
        if (serie == null || !serie.activa || !usuario.equals(serie.propietario)) return false;
        serie.activa = false;

        int indiceActual = actual.getIndiceRecurrente() == null ? -1 : actual.getIndiceRecurrente();
        List<Tarea> propias = tareasMemoria.get(usuario);
        List<Integer> eliminar = propias.stream()
            .filter(item -> Integer.valueOf(serie.id).equals(item.getSerieRecurrenteId()))
            .filter(item -> item.getIndiceRecurrente() != null && item.getIndiceRecurrente() > indiceActual)
            .filter(item -> item.getEstado() != Estado.COMPLETADA)
            .map(Tarea::getId)
            .toList();
        for (List<Tarea> lista : tareasMemoria.values()) {
            lista.removeIf(item -> eliminar.contains(item.getId()));
            for (Tarea item : lista) {
                if (Integer.valueOf(serie.id).equals(item.getSerieRecurrenteId())) {
                    item.setSerieRecurrenteId(null);
                    item.setIndiceRecurrente(null);
                    item.setFrecuenciaRepeticion(null);
                    item.setRepeticionActiva(false);
                }
            }
        }
        eliminar.forEach(propietariosTareasMemoria::remove);
        return true;
    }

    private void prepararOcurrenciaMemoria(Tarea tarea, SerieMemoria serie, int indice, String usuario) {
        int id = contadorId.getAndIncrement();
        tarea.setId(id);
        tarea.setPropietario(usuario);
        tarea.setSerieRecurrenteId(serie.id);
        tarea.setIndiceRecurrente(indice);
        tarea.setFrecuenciaRepeticion(serie.frecuencia);
        tarea.setRepeticionActiva(true);
        for (int i = 0; i < tarea.getSubtareas().size(); i++) {
            tarea.getSubtareas().get(i).setId(i + 1);
        }
        propietariosTareasMemoria.put(id, usuario);
        tareasMemoria.computeIfAbsent(usuario, key -> Collections.synchronizedList(new ArrayList<>())).add(tarea);
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
            Integer serieId = t.getSerieRecurrenteId();
            List<Tarea> tareasCompartir = serieId == null
                ? List.of(t)
                : tareasMemoria.getOrDefault(usuarioOrigen, Collections.emptyList()).stream()
                    .filter(item -> serieId.equals(item.getSerieRecurrenteId()))
                    .toList();
            for (Tarea tareaCompartida : tareasCompartir) {
                boolean yaExiste = listaDestino.stream().anyMatch(item -> item.getId() == tareaCompartida.getId());
                if (!yaExiste) listaDestino.add(tareaCompartida);
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

    public boolean actualizarTarea(int id, String usuario, Tarea cambios) {
        if (Database.isAvailable()) return tareaDao.actualizarTarea(id, usuario, cambios);
        if (usuario == null || !usuario.equals(propietariosTareasMemoria.get(id))) return false;

        List<Tarea> propias = tareasMemoria.get(usuario);
        if (propias == null) return false;
        Tarea seleccionada = propias.stream().filter(t -> t.getId() == id).findFirst().orElse(null);
        if (seleccionada == null) return false;

        Integer serieId = seleccionada.getSerieRecurrenteId();
        List<Tarea> destinos = new ArrayList<>();
        Duration desplazamiento = Duration.ZERO;
        ZoneId zona = null;
        if (serieId != null) {
            if (!"FECHA".equals(cambios.getTipoTarea())) return false;
            SerieMemoria serie = seriesMemoria.get(serieId);
            if (serie == null || seleccionada.getFechaLimite() == null) return false;
            zona = serie.inicio.getZone();
            LocalDateTime fechaAnterior = seleccionada.getFechaLimite().atZone(zona).toLocalDateTime();
            LocalDateTime fechaNueva = cambios.getFechaLimite().atZone(zona).toLocalDateTime();
            desplazamiento = Duration.between(fechaAnterior, fechaNueva);
            destinos.addAll(propias.stream()
                .filter(t -> serieId.equals(t.getSerieRecurrenteId()))
                .filter(t -> t.getIndiceRecurrente() != null
                    && t.getIndiceRecurrente() >= seleccionada.getIndiceRecurrente())
                .filter(t -> t.getId() == id || t.getEstado() != Estado.COMPLETADA)
                .toList());
            if (!desplazamiento.isZero()) {
                serie.inicio = serie.inicio.toLocalDateTime().plus(desplazamiento).atZone(zona);
            }
        } else {
            destinos.add(seleccionada);
        }

        for (Tarea destino : destinos) {
            Instant fecha = cambios.getFechaLimite();
            if (serieId != null && destino.getId() != id && !desplazamiento.isZero()) {
                fecha = destino.getFechaLimite().atZone(zona).toLocalDateTime()
                    .plus(desplazamiento).atZone(zona).toInstant();
            }
            Tarea actualizada = crearTareaEditada(destino, cambios, fecha);
            for (List<Tarea> lista : tareasMemoria.values()) {
                for (int i = 0; i < lista.size(); i++) {
                    if (lista.get(i).getId() == destino.getId()) {
                        lista.set(i, actualizada);
                    }
                }
            }
        }
        return !destinos.isEmpty();
    }

    private Tarea crearTareaEditada(Tarea anterior, Tarea cambios, Instant fechaLimite) {
        Tarea actualizada = "FECHA".equals(cambios.getTipoTarea())
            ? new TareaConFechaLimite(cambios.getTitulo(), cambios.getDescripcion(), cambios.getPrioridad(), fechaLimite)
            : new TareaSimple(cambios.getTitulo(), cambios.getDescripcion(), cambios.getPrioridad());
        actualizada.setId(anterior.getId());
        actualizada.setEstado(anterior.getEstado());
        actualizada.setFechaCreacion(anterior.getFechaCreacion());
        actualizada.setPropietario(anterior.getPropietario());
        actualizada.setSerieRecurrenteId(anterior.getSerieRecurrenteId());
        actualizada.setIndiceRecurrente(anterior.getIndiceRecurrente());
        actualizada.setFrecuenciaRepeticion(anterior.getFrecuenciaRepeticion());
        actualizada.setRepeticionActiva(anterior.isRepeticionActiva());

        boolean[] reutilizadas = new boolean[anterior.getSubtareas().size()];
        for (int indice = 0; indice < cambios.getSubtareas().size(); indice++) {
            Subtarea nueva = cambios.getSubtareas().get(indice);
            Subtarea copia = new Subtarea(nueva.getTitulo());
            copia.setId(indice + 1);
            for (int i = 0; i < anterior.getSubtareas().size(); i++) {
                Subtarea previa = anterior.getSubtareas().get(i);
                if (!reutilizadas[i] && previa.getTitulo().equals(nueva.getTitulo())) {
                    copia.setCompletada(previa.isCompletada());
                    reutilizadas[i] = true;
                    break;
                }
            }
            actualizada.agregarSubtarea(copia);
        }
        return actualizada;
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

    // ─── Subtareas ───────────────────────────────────────────────────────────

    public boolean actualizarSubtarea(int tareaId, String usuario, int subtareaId, boolean completada) {
        if (Database.isAvailable()) {
            return tareaDao.actualizarSubtarea(tareaId, usuario, subtareaId, completada);
        }

        Tarea tarea = buscarTarea(tareaId, usuario);
        if (tarea == null) return false;
        for (Subtarea subtarea : tarea.getSubtareas()) {
            if (subtarea.getId() == subtareaId) {
                subtarea.setCompletada(completada);
                return true;
            }
        }
        return false;
    }
}
