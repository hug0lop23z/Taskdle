package com.taskdle.db;

import com.taskdle.model.Usuario;
import java.sql.*;
import java.time.LocalDate;

public class UsuarioDao {

    public boolean existe(String nombre) {
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT 1 FROM usuarios WHERE nombre = ?")) {
            ps.setString(1, nombre);
            return ps.executeQuery().next();
        } catch (SQLException e) {
            System.err.println("[UsuarioDao] existe: " + e.getMessage());
            return false;
        }
    }

    public boolean insertar(Usuario u) {
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "INSERT INTO usuarios(nombre, password_hash, racha_actual, mejor_racha, ultima_fecha_completada) VALUES(?,?,?,?,?)")) {
            ps.setString(1, u.getNombreUsuario());
            ps.setString(2, u.getPasswordHash());
            ps.setInt(3, u.getRachaActual());
            ps.setInt(4, u.getMejorRacha());
            if (u.getUltimaFechaCompletada() != null)
                ps.setDate(5, Date.valueOf(u.getUltimaFechaCompletada()));
            else
                ps.setNull(5, Types.DATE);
            ps.executeUpdate();
            return true;
        } catch (SQLException e) {
            System.err.println("[UsuarioDao] insertar: " + e.getMessage());
            return false;
        }
    }

    public Usuario buscar(String nombre) {
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT nombre, password_hash, racha_actual, mejor_racha, ultima_fecha_completada FROM usuarios WHERE nombre = ?")) {
            ps.setString(1, nombre);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                Date fecha = rs.getDate("ultima_fecha_completada");
                return new Usuario(
                    rs.getString("nombre"),
                    rs.getString("password_hash"),
                    rs.getInt("racha_actual"),
                    rs.getInt("mejor_racha"),
                    fecha != null ? fecha.toLocalDate() : null
                );
            }
        } catch (SQLException e) {
            System.err.println("[UsuarioDao] buscar: " + e.getMessage());
        }
        return null;
    }

    public boolean actualizarRacha(Usuario u) {
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "UPDATE usuarios SET racha_actual=?, mejor_racha=?, ultima_fecha_completada=? WHERE nombre=?")) {
            ps.setInt(1, u.getRachaActual());
            ps.setInt(2, u.getMejorRacha());
            if (u.getUltimaFechaCompletada() != null)
                ps.setDate(3, Date.valueOf(u.getUltimaFechaCompletada()));
            else
                ps.setNull(3, Types.DATE);
            ps.setString(4, u.getNombreUsuario());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("[UsuarioDao] actualizarRacha: " + e.getMessage());
            return false;
        }
    }
}
