package com.taskdle.api;

import com.sun.net.httpserver.*;
import com.taskdle.model.*;
import com.taskdle.service.GestorUsuarios;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
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
        server.createContext("/api/login",    ServidorWeb::handleLogin);
        server.createContext("/api/registro", ServidorWeb::handleRegistro);
        server.createContext("/api/tareas",   ServidorWeb::handleTareas);
        server.createContext("/api/racha",    ServidorWeb::handleRacha);
        server.createContext("/health",       ex -> respond(ex, 200, "text/plain", "OK"));
        server.setExecutor(null);
        server.start();

        System.out.println("  ✅ Servidor iniciado en puerto " + PUERTO);
        System.out.println("  ℹ  Presiona Ctrl+C para detener.");
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
            respond(ex, 200, "application/json", "{\"ok\":false,\"mensaje\":\"Usuario o contrase\\u00f1a incorrectos\"}");
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

        if (usuario == null || usuario.isBlank() || usuario.length() < 3) {
            respond(ex, 200, "application/json", "{\"ok\":false,\"mensaje\":\"El nombre debe tener al menos 3 caracteres\"}");
            return;
        }
        if (password == null || password.length() < 4) {
            respond(ex, 200, "application/json", "{\"ok\":false,\"mensaje\":\"La contrase\\u00f1a debe tener al menos 4 caracteres\"}");
            return;
        }

        boolean ok = gestor.registrarUsuario(usuario, password);
        if (ok) {
            respond(ex, 200, "application/json", "{\"ok\":true,\"usuario\":\"" + esc(usuario) + "\"}");
        } else {
            respond(ex, 200, "application/json", "{\"ok\":false,\"mensaje\":\"El usuario ya existe. Elige otro nombre\"}");
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

            if (nombre == null || titulo == null || titulo.isBlank()) {
                respond(ex, 400, "application/json", "{\"ok\":false,\"mensaje\":\"Datos inv\\u00e1lidos\"}");
                return;
            }

            Prioridad prio = Prioridad.fromString(prioStr);
            Tarea tarea;
            if ("FECHA".equals(tipo)) {
                String fechaStr = extractJson(body, "fechaLimite");
                LocalDate fecha = LocalDate.parse(fechaStr);
                tarea = new TareaConFechaLimite(titulo, descripcion == null ? "" : descripcion, prio, fecha);
            } else {
                tarea = new TareaSimple(titulo, descripcion == null ? "" : descripcion, prio);
            }

            if (subtareasStr != null && !subtareasStr.isBlank()) {
                for (String sub : subtareasStr.split("\\|")) {
                    if (!sub.trim().isEmpty()) tarea.agregarSubtarea(new Subtarea(sub.trim()));
                }
            }

            int newId = gestor.crearTarea(tarea, nombre);
            if (newId > 0) {
                respond(ex, 200, "application/json", "{\"ok\":true,\"id\":" + newId + "}");
            } else {
                respond(ex, 500, "application/json", "{\"ok\":false,\"mensaje\":\"Error al guardar\"}");
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
                // Si el frontend envía el ID de la subtarea directamente, usarlo
                if (subIdStr != null) {
                    int subtareaId = Integer.parseInt(subIdStr);
                    boolean completada = Boolean.parseBoolean(compStr);
                    gestor.actualizarSubtarea(subtareaId, completada);

                    // Verificar si todas las subtareas están completas
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
        ex.getResponseHeaders().add("Access-Control-Allow-Origin",  "*");
        ex.getResponseHeaders().add("Access-Control-Allow-Methods", "GET,POST,PUT,DELETE,OPTIONS");
        ex.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");
    }

    static boolean preflight(HttpExchange ex) throws IOException {
        if ("OPTIONS".equals(ex.getRequestMethod())) {
            corsHeaders(ex);
            ex.sendResponseHeaders(204, -1);
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
