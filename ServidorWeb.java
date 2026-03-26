import com.sun.net.httpserver.*;
import java.awt.Desktop;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.*;

/**
 * Servidor HTTP ligero (sin dependencias externas) que provee:
 *  - GET  /               → sirve web/index.html
 *  - POST /api/login
 *  - POST /api/registro
 *  - GET  /api/tareas?usuario=X
 *  - POST /api/tareas
 *  - PUT  /api/tareas/{id}/estado
 *  - DELETE /api/tareas/{id}?usuario=X
 */
public class ServidorWeb {

    static GestorUsuarios gestor;
    static final int PUERTO = 8080;
    static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    public static void iniciar() throws Exception {
        gestor = new GestorUsuarios();

        HttpServer server = HttpServer.create(new InetSocketAddress(PUERTO), 0);
        server.createContext("/",             ServidorWeb::handleStatic);
        server.createContext("/api/login",    ServidorWeb::handleLogin);
        server.createContext("/api/registro", ServidorWeb::handleRegistro);
        server.createContext("/api/tareas",   ServidorWeb::handleTareas);
        server.setExecutor(null);
        server.start();

        System.out.println("  ✅ Servidor iniciado en http://localhost:" + PUERTO);
        System.out.println("  ℹ  Presiona Ctrl+C para detener.");

        try {
            Desktop.getDesktop().browse(new URI("http://localhost:" + PUERTO));
        } catch (Exception ignored) {
            System.out.println("  Abre tu navegador en: http://localhost:" + PUERTO);
        }
    }

    // ─── Archivos estáticos ──────────────────────────────────────────────

    static void handleStatic(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath();
        if ("/".equals(path)) path = "/web/index.html";
        else path = "/web" + path;

        Path file = Paths.get("." + path);
        if (!Files.exists(file)) { respond(ex, 404, "text/plain", "404 Not found"); return; }

        String ct = path.endsWith(".html") ? "text/html; charset=UTF-8"
                  : path.endsWith(".css")  ? "text/css"
                  : path.endsWith(".js")   ? "text/javascript"
                  :                          "application/octet-stream";

        byte[] data = Files.readAllBytes(file);
        ex.getResponseHeaders().set("Content-Type", ct);
        ex.sendResponseHeaders(200, data.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(data); }
    }

    // ─── Login ──────────────────────────────────────────────────────────

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

    // ─── Registro ───────────────────────────────────────────────────────

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

    // ─── Tareas ─────────────────────────────────────────────────────────

    static void handleTareas(HttpExchange ex) throws IOException {
        corsHeaders(ex);
        if (preflight(ex)) return;

        String path   = ex.getRequestURI().getPath();
        String method = ex.getRequestMethod();
        String query  = ex.getRequestURI().getQuery();

        // GET /api/tareas?usuario=X
        if ("GET".equals(method) && "/api/tareas".equals(path)) {
            String nombre = queryParam(query, "usuario");
            Usuario u = gestor.getUsuario(nombre);
            if (u == null) { respond(ex, 404, "application/json", "[]"); return; }

            StringBuilder sb = new StringBuilder("[");
            List<Tarea> tareas = u.getTareas();
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

            Usuario u = gestor.getUsuario(nombre);
            if (u == null || titulo == null || titulo.isBlank()) {
                respond(ex, 400, "application/json", "{\"ok\":false,\"mensaje\":\"Datos inv\\u00e1lidos\"}");
                return;
            }

            Prioridad prio = parsePrioridad(prioStr);
            Tarea tarea;
            if ("FECHA".equals(tipo)) {
                String fechaStr = extractJson(body, "fechaLimite");
                LocalDate fecha = LocalDate.parse(fechaStr); // HTML date input → yyyy-MM-dd
                tarea = new TareaConFechaLimite(titulo, descripcion == null ? "" : descripcion, prio, fecha);
            } else {
                tarea = new TareaSimple(titulo, descripcion == null ? "" : descripcion, prio);
            }

            u.agregarTarea(tarea);
            gestor.guardarDatos();
            respond(ex, 200, "application/json", "{\"ok\":true,\"id\":" + tarea.getId() + "}");
            return;
        }

        // PUT /api/tareas/{id}/estado
        Matcher mPut = Pattern.compile("/api/tareas/(\\d+)/estado").matcher(path);
        if ("PUT".equals(method) && mPut.matches()) {
            int    id     = Integer.parseInt(mPut.group(1));
            String body   = readBody(ex);
            String nombre = extractJson(body, "usuario");
            String estStr = extractJson(body, "estado");

            Usuario u = gestor.getUsuario(nombre);
            if (u != null) {
                Tarea t = u.buscarTareaPorId(id);
                if (t != null) {
                    try { t.setEstado(Estado.valueOf(estStr)); } catch (Exception ignored) {}
                    gestor.guardarDatos();
                    respond(ex, 200, "application/json", "{\"ok\":true}");
                    return;
                }
            }
            respond(ex, 404, "application/json", "{\"ok\":false}");
            return;
        }

        // DELETE /api/tareas/{id}?usuario=X
        Matcher mDel = Pattern.compile("/api/tareas/(\\d+)").matcher(path);
        if ("DELETE".equals(method) && mDel.matches()) {
            int    id     = Integer.parseInt(mDel.group(1));
            String nombre = queryParam(query, "usuario");

            Usuario u = gestor.getUsuario(nombre);
            if (u != null && u.eliminarTarea(id)) {
                gestor.guardarDatos();
                respond(ex, 200, "application/json", "{\"ok\":true}");
                return;
            }
            respond(ex, 404, "application/json", "{\"ok\":false}");
            return;
        }

        respond(ex, 404, "application/json", "{\"error\":\"ruta desconocida\"}");
    }

    // ─── Helpers ────────────────────────────────────────────────────────

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
        Matcher m = Pattern.compile("\"" + key + "\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").matcher(json);
        if (m.find()) return m.group(1)
            .replace("\\\"", "\"").replace("\\\\", "\\")
            .replace("\\n", " ").replace("\\r", "").replace("\\t", " ");
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

    static Prioridad parsePrioridad(String s) {
        if (s == null) return Prioridad.MEDIA;
        try { return Prioridad.valueOf(s.toUpperCase()); }
        catch (Exception e) { return Prioridad.MEDIA; }
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
        return sb.append("}").toString();
    }

    static String esc(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }
}
