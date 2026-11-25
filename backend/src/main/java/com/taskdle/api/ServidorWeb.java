package com.taskdle.api;

import com.sun.net.httpserver.*;
import com.taskdle.db.Database;
import com.taskdle.model.*;
import com.taskdle.service.GestorUsuarios;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.*;

public class ServidorWeb {

    static GestorUsuarios gestor;
    static final int PUERTO;
    static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    static {
        String portEnv = System.getenv("PORT");
        PUERTO = (portEnv != null && !portEnv.isBlank()) ? Integer.parseInt(portEnv) : 8080;
    }

    public static void iniciar() throws Exception {
        gestor = new GestorUsuarios();

        HttpServer server = HttpServer.create(new InetSocketAddress(PUERTO), 0);
        server.createContext("/api/login",     ServidorWeb::handleLogin);
        server.createContext("/api/registro",  ServidorWeb::handleRegistro);
        server.createContext("/api/tareas",    ServidorWeb::handleTareas);
        server.createContext("/api/racha",     ServidorWeb::handleRacha);
        server.createContext("/api/db-status", ServidorWeb::handleDbStatus);
        server.createContext("/api/grupos",    ServidorWeb::handleGrupos);
        server.createContext("/health",        ex -> respond(ex, 200, "text/plain", "OK"));
        server.setExecutor(null);
        server.start();

        System.out.println("  ✅ Servidor iniciado en puerto " + PUERTO);
        System.out.println("  ℹ  Presiona Ctrl+C para detener.");
    }

    // ─── Estado de la Base de Datos ──────────────────────────────────────────

    static void handleDbStatus(HttpExchange ex) throws IOException {
        corsHeaders(ex);
        if (preflight(ex)) return;

        boolean available = Database.isAvailable();
        String error = null;
        if (available) {
            try (Connection c = Database.getConnection()) {
                DatabaseMetaData meta = c.getMetaData();
                try (ResultSet rs = meta.getTables(null, null, "usuarios", new String[]{"TABLE"})) {
                    if (!rs.next()) {
                        error = "La tabla 'usuarios' no existe (ejecuta schema.sql en Supabase)";
                    }
                }
            } catch (Exception e) {
                Throwable cause = e.getCause();
                error = e.getMessage() + (cause != null ? " [Causa: " + cause.getClass().getSimpleName() + ": " + cause.getMessage() + "]" : "");
            }
        } else {
            error = "La variable DATABASE_URL no esta configurada en Render";
        }

        String json = "{\"dbConfigurada\":" + available + ",\"error\":" + (error == null ? "null" : "\"" + esc(error) + "\"") + "}";
        respond(ex, 200, "application/json", json);
    }

    // ─── Login ───────────────────────────────────────────────────────────────

    static void handleLogin(HttpExchange ex) throws IOException {
        corsHeaders(ex);
        if (preflight(ex)) return;
        if (!"POST".equals(ex.getRequestMethod())) { respond(ex, 405, "application/json", "{\"ok\":false}"); return; }

        String body     = readBody(ex);
        String usuario  = extractJson(body, "usuario");
        String password = extractJson(body, "password");


        Usuario u = gestor.autenticar(usuario, password);
        if (u != null) {
            respond(ex, 200, "application/json", "{\"ok\":true,\"usuario\":\"" + esc(usuario) + "\"}");
        } else {
            respond(ex, 200, "application/json", "{\"ok\":false,\"mensaje\":\"Usuario o contraseña incorrectos\"}");
        }
    }

    // ─── Registro ────────────────────────────────────────────────────────────

    static void handleRegistro(HttpExchange ex) throws IOException {
        corsHeaders(ex);
        if (preflight(ex)) return;
        if (!"POST".equals(ex.getRequestMethod())) { respond(ex, 405, "application/json", "{\"ok\":false}"); return; }

        String body     = readBody(ex);
        String usuario  = extractJson(body, "usuario");
        String password = extractJson(body, "password");

        int res = gestor.registrarUsuario(usuario, password);
        if (res == 0) {
            respond(ex, 200, "application/json", "{\"ok\":true,\"usuario\":\"" + esc(usuario) + "\"}");
        } else if (res == 1) {
            respond(ex, 200, "application/json", "{\"ok\":false,\"mensaje\":\"Completa los campos (mín. 3 caracteres para usuario y 4 para contraseña)\"}");
        } else if (res == 2) {
            respond(ex, 200, "application/json", "{\"ok\":false,\"mensaje\":\"El usuario ya existe. Elige otro nombre\"}");
        } else {
            respond(ex, 200, "application/json", "{\"ok\":false,\"mensaje\":\"Error de conexion con la base de datos (verifica DATABASE_URL en Render y que ejecutaste schema.sql en Supabase)\"}");
        }
    }

    // ─── Racha ───────────────────────────────────────────────────────────────

    static void handleRacha(HttpExchange ex) throws IOException {
        corsHeaders(ex);
        if (preflight(ex)) return;

        if ("GET".equals(ex.getRequestMethod())) {
            String nombre = queryParam(ex.getRequestURI().getQuery(), "usuario");
            Usuario u = gestor.getUsuario(nombre);
            if (u == null) { respond(ex, 404, "application/json", "{}"); return; }

            String json = "{\"rachaViva\":" + u.getRachaViva() +
                          ",\"rachaActual\":" + u.getRachaActual() +
                          ",\"mejorRacha\":" + u.getMejorRacha() +
                          ",\"enPeligro\":" + u.rachaEnPeligro() + "}";
            respond(ex, 200, "application/json", json);
        } else {
            respond(ex, 405, "application/json", "{\"ok\":false}");
        }
    }

    // ─── Grupos ──────────────────────────────────────────────────────────────

    static void handleGrupos(HttpExchange ex) throws IOException {
        corsHeaders(ex);
        if (preflight(ex)) return;

        String path = ex.getRequestURI().getPath();
        String method = ex.getRequestMethod();
        String query = ex.getRequestURI().getQuery();

        if ("GET".equals(method) && "/api/grupos".equals(path)) {
            String usuario = queryParam(query, "usuario");
            if (usuario == null) { respond(ex, 400, "application/json", "[]"); return; }
            respond(ex, 200, "application/json", gruposJson(gestor.getGrupos(usuario)));
            return;
        }

        if ("POST".equals(method) && "/api/grupos".equals(path)) {
            String body = readBody(ex);
            String propietario = extractJson(body, "usuario");
            String nombre = extractJson(body, "nombre");
            int id = gestor.crearGrupo(propietario, nombre);
            if (id > 0) respond(ex, 200, "application/json", "{\"ok\":true,\"id\":" + id + "}");
            else if (id == -2) respond(ex, 409, "application/json", "{\"ok\":false,\"mensaje\":\"Ya existe un grupo con ese nombre\"}");
            else respond(ex, 400, "application/json", "{\"ok\":false,\"mensaje\":\"Datos inválidos\"}");
            return;
        }

        Matcher mMiembro = Pattern.compile("/api/grupos/(\\d+)/miembros").matcher(path);
        if (mMiembro.matches()) {
            int grupoId = Integer.parseInt(mMiembro.group(1));
            String body = readBody(ex);
            String propietario = extractJson(body, "usuario");
            String miembro = extractJson(body, "miembro");
            if ("POST".equals(method)) {
                boolean ok = gestor.agregarMiembroGrupo(grupoId, propietario, miembro);
                respond(ex, ok ? 200 : 400, "application/json", ok
                    ? "{\"ok\":true}" : "{\"ok\":false,\"mensaje\":\"Grupo o usuario no válido\"}");
            } else if ("DELETE".equals(method)) {
                boolean ok = gestor.quitarMiembroGrupo(grupoId, propietario, miembro);
                respond(ex, ok ? 200 : 400, "application/json", ok
                    ? "{\"ok\":true}" : "{\"ok\":false,\"mensaje\":\"No se pudo quitar al miembro\"}");
            } else {
                respond(ex, 405, "application/json", "{\"ok\":false}");
            }
            return;
        }

        Matcher mGrupo = Pattern.compile("/api/grupos/(\\d+)").matcher(path);
        if ("DELETE".equals(method) && mGrupo.matches()) {
            int grupoId = Integer.parseInt(mGrupo.group(1));
            String usuario = queryParam(query, "usuario");
            boolean ok = gestor.eliminarGrupo(grupoId, usuario);
            respond(ex, ok ? 200 : 400, "application/json", ok ? "{\"ok\":true}" : "{\"ok\":false}");
            return;
        }

        Matcher mShare = Pattern.compile("/api/grupos/(\\d+)/tareas/(\\d+)").matcher(path);
        if ("POST".equals(method) && mShare.matches()) {
            int grupoId = Integer.parseInt(mShare.group(1));
            int tareaId = Integer.parseInt(mShare.group(2));
            String body = readBody(ex);
            String propietario = extractJson(body, "usuario");
            boolean ok = gestor.compartirTareaGrupo(grupoId, tareaId, propietario);
            respond(ex, ok ? 200 : 400, "application/json",
                ok ? "{\"ok\":true}" : "{\"ok\":false,\"mensaje\":\"No tienes permiso para compartir esta tarea con ese grupo\"}");
            return;
        }

        respond(ex, 404, "application/json", "{\"error\":\"ruta desconocida\"}");
    }

    // ─── Tareas ──────────────────────────────────────────────────────────────

    static void handleTareas(HttpExchange ex) throws IOException {
        corsHeaders(ex);
        if (preflight(ex)) return;

        String path   = ex.getRequestURI().getPath();
        String method = ex.getRequestMethod();
        String query  = ex.getRequestURI().getQuery();

        // GET /api/tareas?usuario=X
        if ("GET".equals(method) && "/api/tareas".equals(path)) {
            String nombre = queryParam(query, "usuario");
            if (nombre == null) { respond(ex, 400, "application/json", "[]"); return; }

            StringBuilder sb = new StringBuilder("[");
            List<Tarea> tareas = gestor.getTareas(nombre);
            for (int i = 0; i < tareas.size(); i++) {
                if (i > 0) sb.append(",");
                sb.append(tareaJson(tareas.get(i)));
            }
            respond(ex, 200, "application/json", sb.append("]").toString());
            return;
        }

        // POST /api/tareas
        if ("POST".equals(method) && "/api/tareas".equals(path)) {
            String body        = readBody(ex);
            String nombre      = extractJson(body, "usuario");
            String tipo        = extractJson(body, "tipo");
            String titulo      = extractJson(body, "titulo");
            String descripcion = extractJson(body, "descripcion");
            String prioStr     = extractJson(body, "prioridad");
            String subtareasStr = extractJson(body, "subtareas");
            String compartirCon = extractJson(body, "compartirCon");

            if (nombre == null || titulo == null || titulo.isBlank()) {
                respond(ex, 400, "application/json", "{\"ok\":false,\"mensaje\":\"Datos inválidos\"}");
                return;
            }

            Prioridad prio = Prioridad.fromString(prioStr);
            Tarea tarea;
            if ("FECHA".equals(tipo)) {
                String fechaStr = extractJson(body, "fechaLimite");
                Instant fecha = Instant.parse(fechaStr);
                tarea = new TareaConFechaLimite(titulo, descripcion == null ? "" : descripcion, prio, fecha);
            } else {
                tarea = new TareaSimple(titulo, descripcion == null ? "" : descripcion, prio);
            }

            if (subtareasStr != null && !subtareasStr.isBlank()) {
                for (String sub : subtareasStr.split("\\|")) {
                    if (!sub.trim().isEmpty()) tarea.agregarSubtarea(new Subtarea(sub.trim()));
                }
            }

            int newId = gestor.crearTarea(tarea, nombre, compartirCon);
            if (newId > 0) {
                respond(ex, 200, "application/json", "{\"ok\":true,\"id\":" + newId + "}");
            } else {
                respond(ex, 500, "application/json", "{\"ok\":false,\"mensaje\":\"Error al guardar en base de datos\"}");
            }
            return;
        }

        // POST /api/tareas/{id}/compartir
        Matcher mShare = Pattern.compile("/api/tareas/(\\d+)/compartir").matcher(path);
        if ("POST".equals(method) && mShare.matches()) {
            int id = Integer.parseInt(mShare.group(1));
            String body = readBody(ex);
            String nombre = extractJson(body, "usuario");
            String compCon = extractJson(body, "compartirCon");

            if (nombre != null && compCon != null && !compCon.isBlank()) {
                String target = compCon.trim();
                if (nombre.equalsIgnoreCase(target)) {
                    respond(ex, 400, "application/json", "{\"ok\":false,\"mensaje\":\"No puedes compartir una tarea contigo mismo\"}");
                    return;
                }
                Usuario targetUser = gestor.getUsuario(target);
                if (targetUser == null) {
                    respond(ex, 400, "application/json", "{\"ok\":false,\"mensaje\":\"El usuario '" + esc(target) + "' no existe. Debe registrarse primero.\"}");
                    return;
                }
                boolean ok = gestor.compartirTarea(id, nombre, target);
                if (ok) {
                    respond(ex, 200, "application/json", "{\"ok\":true}");
                    return;
                } else {
                    respond(ex, 400, "application/json", "{\"ok\":false,\"mensaje\":\"Error al compartir la tarea\"}");
                    return;
                }
            }
            respond(ex, 400, "application/json", "{\"ok\":false,\"mensaje\":\"Datos incompletos\"}");
            return;
        }

        // PUT /api/tareas/{id}/fecha
        Matcher mFecha = Pattern.compile("/api/tareas/(\\d+)/fecha").matcher(path);
        if ("PUT".equals(method) && mFecha.matches()) {
            int id = Integer.parseInt(mFecha.group(1));
            String body = readBody(ex);
            String nombre = extractJson(body, "usuario");
            String fechaStr = extractJson(body, "fechaLimite");
            try {
                if (nombre == null || fechaStr == null) throw new IllegalArgumentException("Datos incompletos");
                Instant fecha = Instant.parse(fechaStr);
                boolean ok = gestor.actualizarFechaLimite(id, nombre, fecha);
                respond(ex, ok ? 200 : 403, "application/json", ok
                    ? "{\"ok\":true}"
                    : "{\"ok\":false,\"mensaje\":\"No puedes cambiar la fecha de esta tarea\"}");
            } catch (Exception e) {
                respond(ex, 400, "application/json", "{\"ok\":false,\"mensaje\":\"Fecha límite inválida\"}");
            }
            return;
        }

        // PUT /api/tareas/{id}/estado
        Matcher mPut = Pattern.compile("/api/tareas/(\\d+)/estado").matcher(path);
        if ("PUT".equals(method) && mPut.matches()) {
            int    id     = Integer.parseInt(mPut.group(1));
            String body   = readBody(ex);
            String nombre = extractJson(body, "usuario");
            String estStr = extractJson(body, "estado");

            try {
                Estado nuevoEst = Estado.fromString(estStr);
                boolean ok = gestor.actualizarEstado(id, nombre, nuevoEst);
                boolean rachaCambio = false;
                int rachaActual = 0;
                if (ok && nuevoEst == Estado.COMPLETADA) {
                    rachaCambio = gestor.registrarCompletacion(nombre);
                    Usuario u = gestor.getUsuario(nombre);
                    if (u != null) rachaActual = u.getRachaActual();
                }
                if (ok) {
                    respond(ex, 200, "application/json",
                        "{\"ok\":true,\"rachaCambiada\":" + rachaCambio + ",\"rachaActual\":" + rachaActual + "}");
                } else {
                    respond(ex, 404, "application/json", "{\"ok\":false}");
                }
            } catch (Exception ignored) {
                respond(ex, 400, "application/json", "{\"ok\":false}");
            }
            return;
        }

        // PUT /api/tareas/{id}/subtarea
        Matcher mSub = Pattern.compile("/api/tareas/(\\d+)/subtarea").matcher(path);
        if ("PUT".equals(method) && mSub.matches()) {
            int    id     = Integer.parseInt(mSub.group(1));
            String body   = readBody(ex);
            String nombre = extractJson(body, "usuario");
            String compStr = extractJson(body, "completada");
            String subIdStr = extractJson(body, "subtareaId");

            try {
                if (subIdStr != null) {
                    int subtareaId = Integer.parseInt(subIdStr);
                    boolean completada = Boolean.parseBoolean(compStr);
                    gestor.actualizarSubtarea(subtareaId, completada);
                    
                    Tarea t = gestor.buscarTarea(id, nombre);
                    boolean rachaCambio = false;
                    int rachaActual = 0;
                    String nuevoEstado = t != null ? t.getEstado().name() : "PENDIENTE";
                    
                    if (t != null) {
                        boolean allComplete = t.getSubtareas().stream().allMatch(Subtarea::isCompletada);
                        if (allComplete && t.getEstado() != Estado.COMPLETADA) {
                            gestor.actualizarEstado(id, nombre, Estado.COMPLETADA);
                            rachaCambio = gestor.registrarCompletacion(nombre);
                            Usuario u = gestor.getUsuario(nombre);
                            if (u != null) rachaActual = u.getRachaActual();
                            nuevoEstado = Estado.COMPLETADA.name();
                        } else if (!allComplete && t.getEstado() == Estado.COMPLETADA) {
                            gestor.actualizarEstado(id, nombre, Estado.EN_PROGRESO);
                            nuevoEstado = Estado.EN_PROGRESO.name();
                        }
                    }
                    
                    respond(ex, 200, "application/json",
                        "{\"ok\":true,\"rachaCambiada\":" + rachaCambio +
                        ",\"rachaActual\":" + rachaActual +
                        ",\"nuevoEstado\":\"" + nuevoEstado + "\"}");
                } else {
                    respond(ex, 400, "application/json", "{\"ok\":false,\"mensaje\":\"Se requiere subtareaId\"}");
                }
            } catch (Exception e) {
                respond(ex, 500, "application/json", "{\"ok\":false}");
            }
            return;
        }

        // DELETE /api/tareas/{id}?usuario=X
        Matcher mDel = Pattern.compile("/api/tareas/(\\d+)").matcher(path);
        if ("DELETE".equals(method) && mDel.matches()) {
            int    id     = Integer.parseInt(mDel.group(1));
            String nombre = queryParam(query, "usuario");
            if (gestor.eliminarTarea(id, nombre)) {
                respond(ex, 200, "application/json", "{\"ok\":true}");
            } else {
                respond(ex, 404, "application/json", "{\"ok\":false}");
            }
            return;
        }

        respond(ex, 404, "application/json", "{\"error\":\"ruta desconocida\"}");
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    static void respond(HttpExchange ex, int code, String ct, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", ct + "; charset=UTF-8");
        ex.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(bytes); }
    }

    static void corsHeaders(HttpExchange ex) {
        Headers h = ex.getResponseHeaders();
        h.set("Access-Control-Allow-Origin",  "*");
        h.set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        h.set("Access-Control-Allow-Headers", "Content-Type, Authorization");
    }

    static boolean preflight(HttpExchange ex) throws IOException {
        if ("OPTIONS".equalsIgnoreCase(ex.getRequestMethod())) {
            corsHeaders(ex);
            ex.sendResponseHeaders(204, -1);
            ex.close();
            return true;
        }
        return false;
    }

    static String readBody(HttpExchange ex) throws IOException {
        try (InputStream is = ex.getRequestBody()) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    static String extractJson(String json, String key) {
        if (json == null) return null;
        Matcher m = Pattern.compile("\"" + key + "\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"|\"" + key + "\"\\s*:\\s*(true|false|null|[\\d.]+)").matcher(json);
        if (m.find()) {
            if (m.group(1) != null) return m.group(1).replace("\\\"", "\"").replace("\\\\", "\\").replace("\\n", " ").replace("\\r", "").replace("\\t", " ");
            return m.group(2);
        }
        return null;
    }

    static String queryParam(String query, String key) {
        if (query == null) return null;
        for (String part : query.split("&")) {
            String[] kv = part.split("=", 2);
            if (kv.length == 2 && kv[0].equals(key)) {
                try { return URLDecoder.decode(kv[1], StandardCharsets.UTF_8); }
                catch (Exception e) { return kv[1]; }
            }
        }
        return null;
    }

    static String gruposJson(List<com.taskdle.model.Grupo> grupos) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < grupos.size(); i++) {
            if (i > 0) sb.append(",");
            com.taskdle.model.Grupo g = grupos.get(i);
            sb.append("{\"id\":").append(g.getId())
              .append(",\"nombre\":\"").append(esc(g.getNombre()))
              .append("\",\"propietario\":\"").append(esc(g.getPropietario()))
              .append("\",\"miembros\":[");
            for (int j = 0; j < g.getMiembros().size(); j++) {
                if (j > 0) sb.append(",");
                sb.append("\"").append(esc(g.getMiembros().get(j))).append("\"");
            }
            sb.append("]}");
        }
        return sb.append("]").toString();
    }

    static String tareaJson(Tarea t) {
        StringBuilder sb = new StringBuilder("{");
        sb.append("\"id\":").append(t.getId()).append(",");
        sb.append("\"tipo\":\"").append(esc(t.getTipoTarea())).append("\",");
        sb.append("\"titulo\":\"").append(esc(t.getTitulo())).append("\",");
        sb.append("\"descripcion\":\"").append(esc(t.getDescripcion())).append("\",");
        sb.append("\"prioridad\":\"").append(t.getPrioridad()).append("\",");
        sb.append("\"estado\":\"").append(t.getEstado()).append("\",");
        sb.append("\"fechaCreacion\":\"").append(t.getFechaCreacion().format(FMT)).append("\",");
        sb.append("\"infoAdicional\":\"").append(esc(t.getInfoAdicional())).append("\"");
        if (t instanceof TareaConFechaLimite tcfl) {
            sb.append(",\"fechaLimite\":\"").append(tcfl.getFechaLimite()).append("\"");
            sb.append(",\"vencida\":").append(tcfl.estaVencida());
            sb.append(",\"diasRestantes\":").append(tcfl.getDiasRestantes());
        }
        sb.append(",\"subtareas\":[");
        List<Subtarea> subs = t.getSubtareas();
        for (int i = 0; i < subs.size(); i++) {
            if (i > 0) sb.append(",");
            Subtarea st = subs.get(i);
            sb.append("{\"id\":").append(st.getId())
              .append(",\"titulo\":\"").append(esc(st.getTitulo()))
              .append("\",\"completada\":").append(st.isCompletada()).append("}");
        }
        sb.append("]");
        return sb.append("}").toString();
    }

    static String esc(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }
}
