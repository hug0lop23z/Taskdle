package com.taskdle.db;

import com.taskdle.model.*;
import java.sql.*;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

public class TareaDao {

    // ─── TAREAS ──────────────────────────────────────────────────────────────

    public List<Tarea> listarPorUsuario(String usuario) {
        List<Tarea> lista = new ArrayList<>();
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT DISTINCT t.id, t.dueno, t.tipo, t.titulo, t.descripcion, t.prioridad, t.estado, t.fecha_creacion, t.fecha_limite, " +
                 "t.recurrencia_id, t.indice_recurrencia, tr.frecuencia, tr.activa AS recurrencia_activa " +
                 "FROM tareas t " +
                 "LEFT JOIN tareas_compartidas tc ON t.id = tc.tarea_id " +
                 "LEFT JOIN tareas_recurrentes tr ON t.recurrencia_id = tr.id " +
                 "WHERE t.dueno = ? OR tc.usuario = ? " +
                 "ORDER BY t.id")) {
            ps.setString(1, usuario);
            ps.setString(2, usuario);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                Tarea t = mapTarea(rs);
                if (t != null) {
                    t.getSubtareas().addAll(listarSubtareas(t.getId(), c));
                    lista.add(t);
                }
            }
        } catch (SQLException e) {
            System.err.println("[TareaDao] listarPorUsuario: " + e.getMessage());
        }
        return lista;
    }

    public Tarea buscarPorId(int id, String usuario) {
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT DISTINCT t.id, t.dueno, t.tipo, t.titulo, t.descripcion, t.prioridad, t.estado, t.fecha_creacion, t.fecha_limite, " +
                 "t.recurrencia_id, t.indice_recurrencia, tr.frecuencia, tr.activa AS recurrencia_activa " +
                 "FROM tareas t " +
                 "LEFT JOIN tareas_compartidas tc ON t.id = tc.tarea_id " +
                 "LEFT JOIN tareas_recurrentes tr ON t.recurrencia_id = tr.id " +
                 "WHERE t.id = ? AND (t.dueno = ? OR tc.usuario = ?)")) {
            ps.setInt(1, id);
            ps.setString(2, usuario);
            ps.setString(3, usuario);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                Tarea t = mapTarea(rs);
                if (t != null) t.getSubtareas().addAll(listarSubtareas(id, c));
                return t;
            }
        } catch (SQLException e) {
            System.err.println("[TareaDao] buscarPorId: " + e.getMessage());
        }
        return null;
    }

    public int insertar(Tarea t, String usuario) {
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "INSERT INTO tareas(dueno, tipo, titulo, descripcion, prioridad, estado, fecha_creacion, fecha_limite) " +
                 "VALUES(?,?,?,?,?,?,?,?) RETURNING id")) {
            ps.setString(1, usuario);
            ps.setString(2, t.getTipoTarea());
            ps.setString(3, t.getTitulo());
            ps.setString(4, t.getDescripcion());
            ps.setString(5, t.getPrioridad().name());
            ps.setString(6, t.getEstado().name());
            ps.setTimestamp(7, Timestamp.valueOf(t.getFechaCreacion()));
            Instant fl = t.getFechaLimite();
            if (fl != null) ps.setTimestamp(8, Timestamp.from(fl));
            else ps.setNull(8, Types.TIMESTAMP_WITH_TIMEZONE);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                int newId = rs.getInt(1);
                t.setId(newId);
                for (int i = 0; i < t.getSubtareas().size(); i++) {
                    insertarSubtarea(t.getSubtareas().get(i), newId, i, c);
                }
                return newId;
            }
        } catch (SQLException e) {
            System.err.println("[TareaDao] insertar: " + e.getMessage());
        }
        return -1;
    }

    public List<Integer> insertarSerie(Tarea tarea, String usuario, FrecuenciaRepeticion frecuencia,
                                      ZoneId zonaHoraria, int cantidadOcurrencias) {
        List<Integer> ids = new ArrayList<>();
        if (!(tarea instanceof TareaConFechaLimite conFecha) || cantidadOcurrencias < 1) return ids;

        try (Connection c = Database.getConnection()) {
            c.setAutoCommit(false);
            try {
                int serieId;
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO tareas_recurrentes(dueno, frecuencia, fecha_base, zona_horaria) VALUES(?,?,?,?) RETURNING id")) {
                    ps.setString(1, usuario);
                    ps.setString(2, frecuencia.name());
                    ps.setTimestamp(3, Timestamp.from(conFecha.getFechaLimite()));
                    ps.setString(4, zonaHoraria.getId());
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) throw new SQLException("No se pudo crear la serie recurrente");
                        serieId = rs.getInt(1);
                    }
                }

                ZonedDateTime inicio = conFecha.getFechaLimite().atZone(zonaHoraria);
                for (int indice = 0; indice < cantidadOcurrencias; indice++) {
                    Instant fecha = frecuencia.ocurrenciaDesde(inicio, indice).toInstant();
                    ids.add(insertarOcurrencia(tarea, usuario, serieId, indice, fecha, c));
                }
                c.commit();
            } catch (SQLException | RuntimeException e) {
                c.rollback();
                throw e;
            }
        } catch (Exception e) {
            System.err.println("[TareaDao] insertarSerie: " + e.getMessage());
            ids.clear();
        }
        return ids;
    }

    public int asegurarOcurrenciasFuturas(int tareaId, String usuario, int cantidadOcurrencias) {
        try (Connection c = Database.getConnection()) {
            c.setAutoCommit(false);
            try {
                int serieId;
                int indiceActual;
                FrecuenciaRepeticion frecuencia;
                ZonedDateTime inicio;
                String propietarioSerie;
                try (PreparedStatement ps = c.prepareStatement(
                        "SELECT tr.id, tr.dueno AS propietario_serie, tr.frecuencia, tr.fecha_base, tr.zona_horaria, t.indice_recurrencia " +
                        "FROM tareas t JOIN tareas_recurrentes tr ON t.recurrencia_id=tr.id " +
                        "WHERE t.id=? AND (t.dueno=? OR EXISTS " +
                        "(SELECT 1 FROM tareas_compartidas tc WHERE tc.tarea_id=t.id AND tc.usuario=?)) " +
                        "AND tr.activa=TRUE FOR UPDATE OF tr")) {
                    ps.setInt(1, tareaId);
                    ps.setString(2, usuario);
                    ps.setString(3, usuario);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            c.rollback();
                            return 0;
                        }
                        serieId = rs.getInt("id");
                        propietarioSerie = rs.getString("propietario_serie");
                        frecuencia = FrecuenciaRepeticion.fromString(rs.getString("frecuencia"));
                        inicio = rs.getTimestamp("fecha_base").toInstant()
                            .atZone(ZoneId.of(rs.getString("zona_horaria")));
                        indiceActual = rs.getInt("indice_recurrencia");
                    }
                }

                Tarea plantilla = buscarPlantillaSerie(tareaId, c);
                if (!(plantilla instanceof TareaConFechaLimite)) {
                    c.rollback();
                    return 0;
                }

                int pendientes = contarOcurrenciasNoCompletadas(serieId, c);
                int siguienteIndice = siguienteIndiceSerie(serieId, indiceActual, c);
                int creadas = 0;
                while (pendientes < cantidadOcurrencias) {
                    Instant fecha = frecuencia.ocurrenciaDesde(inicio, siguienteIndice).toInstant();
                    int nuevoId = insertarOcurrencia(plantilla, propietarioSerie, serieId, siguienteIndice, fecha, c);
                    copiarCompartidos(tareaId, nuevoId, c);
                    siguienteIndice++;
                    pendientes++;
                    creadas++;
                }
                c.commit();
                return creadas;
            } catch (SQLException | RuntimeException e) {
                c.rollback();
                throw e;
            }
        } catch (Exception e) {
            System.err.println("[TareaDao] asegurarOcurrenciasFuturas: " + e.getMessage());
            return -1;
        }
    }

    public boolean detenerSerie(int tareaId, String usuario) {
        try (Connection c = Database.getConnection()) {
            c.setAutoCommit(false);
            try {
                int serieId;
                int indiceActual;
                try (PreparedStatement ps = c.prepareStatement(
                        "SELECT tr.id, t.indice_recurrencia FROM tareas t " +
                        "JOIN tareas_recurrentes tr ON t.recurrencia_id=tr.id " +
                        "WHERE t.id=? AND t.dueno=? AND tr.activa=TRUE FOR UPDATE OF tr")) {
                    ps.setInt(1, tareaId);
                    ps.setString(2, usuario);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            c.rollback();
                            return false;
                        }
                        serieId = rs.getInt("id");
                        indiceActual = rs.getInt("indice_recurrencia");
                    }
                }

                try (PreparedStatement ps = c.prepareStatement(
                        "UPDATE tareas_recurrentes SET activa=FALSE WHERE id=?")) {
                    ps.setInt(1, serieId);
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = c.prepareStatement(
                        "DELETE FROM tareas WHERE recurrencia_id=? AND indice_recurrencia>? AND estado<>'COMPLETADA'")) {
                    ps.setInt(1, serieId);
                    ps.setInt(2, indiceActual);
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = c.prepareStatement(
                        "UPDATE tareas SET recurrencia_id=NULL, indice_recurrencia=NULL WHERE recurrencia_id=?")) {
                    ps.setInt(1, serieId);
                    ps.executeUpdate();
                }
                c.commit();
                return true;
            } catch (SQLException | RuntimeException e) {
                c.rollback();
                throw e;
            }
        } catch (Exception e) {
            System.err.println("[TareaDao] detenerSerie: " + e.getMessage());
            return false;
        }
    }

    public boolean compartir(int tareaId, String usuarioDestino) {
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "INSERT INTO tareas_compartidas(tarea_id, usuario) " +
                 "SELECT t.id, ? FROM tareas t WHERE t.id=? OR t.recurrencia_id=" +
                 "(SELECT recurrencia_id FROM tareas WHERE id=? AND recurrencia_id IS NOT NULL) " +
                 "ON CONFLICT DO NOTHING")) {
            ps.setString(1, usuarioDestino);
            ps.setInt(2, tareaId);
            ps.setInt(3, tareaId);
            return ps.executeUpdate() >= 0;
        } catch (SQLException e) {
            System.err.println("[TareaDao] compartir: " + e.getMessage());
            return false;
        }
    }

    public boolean actualizarEstado(int id, String usuario, Estado nuevoEstado) {
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "UPDATE tareas SET estado=? WHERE id=? AND (dueno=? OR id IN (SELECT tarea_id FROM tareas_compartidas WHERE usuario=?))")) {
            ps.setString(1, nuevoEstado.name());
            ps.setInt(2, id);
            ps.setString(3, usuario);
            ps.setString(4, usuario);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("[TareaDao] actualizarEstado: " + e.getMessage());
            return false;
        }
    }

    public boolean actualizarFechaLimite(int id, String usuario, Instant fechaLimite) {
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "UPDATE tareas SET fecha_limite=? WHERE id=? AND dueno=? AND tipo='FECHA'")) {
            ps.setTimestamp(1, Timestamp.from(fechaLimite));
            ps.setInt(2, id);
            ps.setString(3, usuario);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("[TareaDao] actualizarFechaLimite: " + e.getMessage());
            return false;
        }
    }

    public boolean eliminar(int id, String usuario) {
        try (Connection c = Database.getConnection()) {
            // Si el usuario era solo un invitado con el que se compartía, quitar el share
            try (PreparedStatement ps = c.prepareStatement(
                     "DELETE FROM tareas_compartidas WHERE tarea_id=? AND usuario=?")) {
                ps.setInt(1, id);
                ps.setString(2, usuario);
                if (ps.executeUpdate() > 0) return true;
            }
            // Si el usuario es el dueño, eliminar la tarea por completo
            try (PreparedStatement ps = c.prepareStatement(
                     "DELETE FROM tareas WHERE id=? AND dueno=?")) {
                ps.setInt(1, id);
                ps.setString(2, usuario);
                return ps.executeUpdate() > 0;
            }
        } catch (SQLException e) {
            System.err.println("[TareaDao] eliminar: " + e.getMessage());
            return false;
        }
    }

    // ─── SUBTAREAS ───────────────────────────────────────────────────────────

    private List<Subtarea> listarSubtareas(int tareaId, Connection c) throws SQLException {
        List<Subtarea> lista = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(
                 "SELECT id, titulo, completada, orden FROM subtareas WHERE tarea_id=? ORDER BY orden")) {
            ps.setInt(1, tareaId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                lista.add(new Subtarea(
                    rs.getInt("id"),
                    rs.getString("titulo"),
                    rs.getBoolean("completada"),
                    rs.getInt("orden")
                ));
            }
        }
        return lista;
    }

    private void insertarSubtarea(Subtarea s, int tareaId, int orden, Connection c) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                 "INSERT INTO subtareas(tarea_id, titulo, completada, orden) VALUES(?,?,?,?) RETURNING id")) {
            ps.setInt(1, tareaId);
            ps.setString(2, s.getTitulo());
            ps.setBoolean(3, s.isCompletada());
            ps.setInt(4, orden);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) s.setId(rs.getInt(1));
        }
    }

    private int insertarOcurrencia(Tarea plantilla, String usuario, int serieId, int indice,
                                   Instant fechaLimite, Connection c) throws SQLException {
        int id;
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO tareas(dueno, tipo, titulo, descripcion, prioridad, estado, fecha_creacion, fecha_limite, recurrencia_id, indice_recurrencia) " +
                "VALUES(?,?,?,?,?,?,?,?,?,?) RETURNING id")) {
            ps.setString(1, usuario);
            ps.setString(2, plantilla.getTipoTarea());
            ps.setString(3, plantilla.getTitulo());
            ps.setString(4, plantilla.getDescripcion());
            ps.setString(5, plantilla.getPrioridad().name());
            ps.setString(6, Estado.PENDIENTE.name());
            ps.setTimestamp(7, Timestamp.valueOf(LocalDateTime.now()));
            ps.setTimestamp(8, Timestamp.from(fechaLimite));
            ps.setInt(9, serieId);
            ps.setInt(10, indice);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new SQLException("No se pudo crear una ocurrencia");
                id = rs.getInt(1);
            }
        }

        for (int i = 0; i < plantilla.getSubtareas().size(); i++) {
            insertarSubtarea(new Subtarea(plantilla.getSubtareas().get(i).getTitulo()), id, i, c);
        }
        return id;
    }

    private Tarea buscarPlantillaSerie(int tareaId, Connection c) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT id, dueno, tipo, titulo, descripcion, prioridad, estado, fecha_creacion, fecha_limite, " +
                "recurrencia_id, indice_recurrencia, (SELECT frecuencia FROM tareas_recurrentes WHERE id=t.recurrencia_id) AS frecuencia, " +
                "(SELECT activa FROM tareas_recurrentes WHERE id=t.recurrencia_id) AS recurrencia_activa " +
                "FROM tareas t WHERE id=?")) {
            ps.setInt(1, tareaId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                Tarea tarea = mapTarea(rs);
                if (tarea != null) tarea.getSubtareas().addAll(listarSubtareas(tareaId, c));
                return tarea;
            }
        }
    }

    private int contarOcurrenciasNoCompletadas(int serieId, Connection c) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT COUNT(*) FROM tareas WHERE recurrencia_id=? AND estado<>'COMPLETADA'")) {
            ps.setInt(1, serieId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    private int siguienteIndiceSerie(int serieId, int indiceActual, Connection c) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT COALESCE(MAX(indice_recurrencia), ?) + 1 FROM tareas WHERE recurrencia_id=?")) {
            ps.setInt(1, indiceActual);
            ps.setInt(2, serieId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : indiceActual + 1;
            }
        }
    }

    private void copiarCompartidos(int tareaOrigen, int tareaNueva, Connection c) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO tareas_compartidas(tarea_id, usuario) " +
                "SELECT ?, usuario FROM tareas_compartidas WHERE tarea_id=? ON CONFLICT DO NOTHING")) {
            ps.setInt(1, tareaNueva);
            ps.setInt(2, tareaOrigen);
            ps.executeUpdate();
        }
    }

    public boolean actualizarSubtarea(int subtareaId, boolean completada) {
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "UPDATE subtareas SET completada=? WHERE id=?")) {
            ps.setBoolean(1, completada);
            ps.setInt(2, subtareaId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("[TareaDao] actualizarSubtarea: " + e.getMessage());
            return false;
        }
    }

    // ─── Mapper ──────────────────────────────────────────────────────────────

    private Tarea mapTarea(ResultSet rs) throws SQLException {
        int id = rs.getInt("id");
        String tipo   = rs.getString("tipo");
        String titulo = rs.getString("titulo");
        String desc   = rs.getString("descripcion");
        Prioridad p   = Prioridad.fromString(rs.getString("prioridad"));
        Estado e      = Estado.fromString(rs.getString("estado"));
        LocalDateTime fc = rs.getTimestamp("fecha_creacion").toLocalDateTime();
        Timestamp fl = rs.getTimestamp("fecha_limite");

        Tarea tarea;
        if ("FECHA".equals(tipo) && fl != null) {
            tarea = new TareaConFechaLimite(id, titulo, desc, p, e, fc, fl.toInstant());
        } else {
            tarea = new TareaSimple(id, titulo, desc, p, e, fc);
        }
        tarea.setPropietario(rs.getString("dueno"));
        int serieId = rs.getInt("recurrencia_id");
        if (!rs.wasNull()) tarea.setSerieRecurrenteId(serieId);
        int indice = rs.getInt("indice_recurrencia");
        if (!rs.wasNull()) tarea.setIndiceRecurrente(indice);
        String frecuencia = rs.getString("frecuencia");
        if (frecuencia != null) tarea.setFrecuenciaRepeticion(FrecuenciaRepeticion.fromString(frecuencia));
        tarea.setRepeticionActiva(rs.getBoolean("recurrencia_activa"));
        return tarea;
    }
}
