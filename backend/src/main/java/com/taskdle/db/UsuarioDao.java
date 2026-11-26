package com.taskdle.db;

import com.taskdle.model.Usuario;
import java.sql.*;

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
                 "INSERT INTO usuarios(nombre, password_hash) VALUES(?,?)")) {
            ps.setString(1, u.getNombreUsuario());
            ps.setString(2, u.getPasswordHash());
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
                 "SELECT nombre, password_hash FROM usuarios WHERE nombre = ?")) {
            ps.setString(1, nombre);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return new Usuario(rs.getString("nombre"), rs.getString("password_hash"));
            }
        } catch (SQLException e) {
            System.err.println("[UsuarioDao] buscar: " + e.getMessage());
        }
        return null;
    }

}
