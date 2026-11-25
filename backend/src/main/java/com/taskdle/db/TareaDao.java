package com.taskdle.db;

import com.taskdle.model.*;
import java.sql.*;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class TareaDao {

    // ─── TAREAS ──────────────────────────────────────────────────────────────

    public List<Tarea> listarPorUsuario(String usuario) {
        List<Tarea> lista = new ArrayList<>();
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT DISTINCT t.id, t.tipo, t.titulo, t.descripcion, t.prioridad, t.estado, t.fecha_creacion, t.fecha_limite " +
                 "FROM tareas t " +
                 "LEFT JOIN tareas_compartidas tc ON t.id = tc.tarea_id " +
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
                 "SELECT DISTINCT t.id, t.tipo, t.titulo, t.descripcion, t.prioridad, t.estado, t.fecha_creacion, t.fecha_limite " +
                 "FROM tareas t " +
                 "LEFT JOIN tareas_compartidas tc ON t.id = tc.tarea_id " +
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

    public boolean compartir(int tareaId, String usuarioDestino) {
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "INSERT INTO tareas_compartidas(tarea_id, usuario) VALUES(?, ?) ON CONFLICT DO NOTHING")) {
            ps.setInt(1, tareaId);
            ps.setString(2, usuarioDestino);
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

        if ("FECHA".equals(tipo) && fl != null) {
            return new TareaConFechaLimite(id, titulo, desc, p, e, fc, fl.toInstant());
        } else {
            return new TareaSimple(id, titulo, desc, p, e, fc);
        }
    }
}
