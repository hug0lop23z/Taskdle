package com.taskdle.db;

import com.taskdle.model.Grupo;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class GrupoDao {
    public List<Grupo> listar(String usuario) {
        List<Grupo> grupos = new ArrayList<>();
        String sql = "SELECT DISTINCT g.id, g.nombre, g.propietario " +
                     "FROM grupos g LEFT JOIN grupo_miembros gm ON gm.grupo_id=g.id " +
                     "WHERE g.propietario=? OR gm.usuario=? ORDER BY g.id";
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, usuario);
            ps.setString(2, usuario);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) grupos.add(new Grupo(rs.getInt("id"), rs.getString("nombre"), rs.getString("propietario")));
            }
            for (Grupo grupo : grupos) cargarMiembros(grupo, c);
        } catch (SQLException e) {
            System.err.println("[GrupoDao] listar: " + e.getMessage());
        }
        return grupos;
    }

    private void cargarMiembros(Grupo grupo, Connection c) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT usuario FROM grupo_miembros WHERE grupo_id=? ORDER BY usuario")) {
            ps.setInt(1, grupo.getId());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) grupo.getMiembros().add(rs.getString("usuario"));
            }
        }
    }

    public int crear(String propietario, String nombre) {
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "INSERT INTO grupos(nombre, propietario) VALUES(?,?) RETURNING id")) {
            ps.setString(1, nombre);
            ps.setString(2, propietario);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : -1;
            }
        } catch (SQLException e) {
            System.err.println("[GrupoDao] crear: " + e.getMessage());
            return -2;
        }
    }

    public boolean esPropietario(int grupoId, String usuario) {
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT 1 FROM grupos WHERE id=? AND propietario=?")) {
            ps.setInt(1, grupoId);
            ps.setString(2, usuario);
            return ps.executeQuery().next();
        } catch (SQLException e) {
            return false;
        }
    }

    public boolean agregarMiembro(int grupoId, String miembro) {
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "INSERT INTO grupo_miembros(grupo_id, usuario) VALUES(?,?) ON CONFLICT DO NOTHING")) {
            ps.setInt(1, grupoId);
            ps.setString(2, miembro);
            return ps.executeUpdate() >= 0;
        } catch (SQLException e) {
            return false;
        }
    }

    public boolean quitarMiembro(int grupoId, String miembro) {
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "DELETE FROM grupo_miembros WHERE grupo_id=? AND usuario=?")) {
            ps.setInt(1, grupoId);
            ps.setString(2, miembro);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            return false;
        }
    }

    public boolean eliminar(int grupoId, String propietario) {
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "DELETE FROM grupos WHERE id=? AND propietario=?")) {
            ps.setInt(1, grupoId);
            ps.setString(2, propietario);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            return false;
        }
    }

    public boolean compartirTarea(int grupoId, int tareaId, String propietario) {
        if (!esPropietario(grupoId, propietario)) return false;
        try (Connection c = Database.getConnection();
             PreparedStatement miembros = c.prepareStatement(
                 "SELECT usuario FROM grupo_miembros WHERE grupo_id=?");
             PreparedStatement share = c.prepareStatement(
                 "INSERT INTO tareas_compartidas(tarea_id, usuario) VALUES(?,?) ON CONFLICT DO NOTHING");
             PreparedStatement ownership = c.prepareStatement(
                 "SELECT 1 FROM tareas WHERE id=? AND dueno=?")) {
            ownership.setInt(1, tareaId);
            ownership.setString(2, propietario);
            if (!ownership.executeQuery().next()) return false;
            miembros.setInt(1, grupoId);
            try (ResultSet rs = miembros.executeQuery()) {
                while (rs.next()) {
                    share.setInt(1, tareaId);
                    share.setString(2, rs.getString("usuario"));
                    share.executeUpdate();
                }
            }
            return true;
        } catch (SQLException e) {
            System.err.println("[GrupoDao] compartir tarea: " + e.getMessage());
            return false;
        }
    }

    public List<String> miembros(int grupoId) {
        List<String> miembros = new ArrayList<>();
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT usuario FROM grupo_miembros WHERE grupo_id=? ORDER BY usuario")) {
            ps.setInt(1, grupoId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) miembros.add(rs.getString("usuario"));
            }
        } catch (SQLException e) {}
        return miembros;
    }
}
