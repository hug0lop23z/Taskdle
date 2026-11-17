import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Gestiona la persistencia de datos en un archivo de texto plano (CSV).
 *
 * Formato del archivo datos.csv:
 *   USUARIO;nombreUsuario;contraseña
 *   TAREA_SIMPLE;nombreUsuario;id;titulo;descripcion;prioridad;estado;fechaCreacion
 *   TAREA_FECHA;nombreUsuario;id;titulo;descripcion;prioridad;estado;fechaCreacion;fechaLimite
 *
 * Los campos con posibles punto y coma se escapan con \\; al guardar.
 */
public class PersistenciaArchivo {

    private static final String ARCHIVO = "datos.csv";
    private static final String SEP     = ";";

    // ─── Guardar ──────────────────────────────────────────────────────────

    /**
     * Sobrescribe el archivo con todos los datos actuales de los usuarios.
     */
    public static void guardar(Map<String, Usuario> usuarios) {
        try (BufferedWriter bw = new BufferedWriter(
                new OutputStreamWriter(
                        new FileOutputStream(ARCHIVO), StandardCharsets.UTF_8))) {

            for (Usuario u : usuarios.values()) {
                // Línea de usuario
                String fechaStr = u.getUltimaFechaCompletada() != null ? u.getUltimaFechaCompletada().toString() : "NULL";
                bw.write("USUARIO" + SEP + esc(u.getNombreUsuario()) + SEP + esc(u.getContrasena())
                        + SEP + u.getRachaActual() + SEP + u.getMejorRacha() + SEP + fechaStr);
                bw.newLine();

                // Líneas de tareas
                for (Tarea t : u.getTareas()) {
                    if (t instanceof TareaConFechaLimite tcfl) {
                        bw.write("TAREA_FECHA" + SEP
                                + esc(u.getNombreUsuario()) + SEP
                                + t.getId() + SEP
                                + esc(t.getTitulo()) + SEP
                                + esc(t.getDescripcion()) + SEP
                                + t.getPrioridad() + SEP
                                + t.getEstado() + SEP
                                + t.getFechaCreacion() + SEP
                                + tcfl.getFechaLimite());
                    } else {
                        bw.write("TAREA_SIMPLE" + SEP
                                + esc(u.getNombreUsuario()) + SEP
                                + t.getId() + SEP
                                + esc(t.getTitulo()) + SEP
                                + esc(t.getDescripcion()) + SEP
                                + t.getPrioridad() + SEP
                                + t.getEstado() + SEP
                                + t.getFechaCreacion());
                    }
                    bw.newLine();

                    for (Subtarea st : t.getSubtareas()) {
                        bw.write("SUBTAREA" + SEP
                                + esc(u.getNombreUsuario()) + SEP
                                + t.getId() + SEP
                                + esc(st.getTitulo()) + SEP
                                + st.isCompletada());
                        bw.newLine();
                    }
                }
            }

        } catch (IOException e) {
            System.err.println("  [ERROR] No se pudo guardar los datos: " + e.getMessage());
        }
    }

    // ─── Cargar ───────────────────────────────────────────────────────────

    /**
     * Lee el archivo y reconstruye el mapa de usuarios con sus tareas.
     * Si el archivo no existe, retorna un mapa vacío (primera ejecución).
     */
    public static Map<String, Usuario> cargar() {
        Map<String, Usuario> usuarios = new LinkedHashMap<>();
        Path path = Paths.get(ARCHIVO);

        if (!Files.exists(path)) {
            return usuarios; // primera ejecución, sin datos previos
        }

        int maxId = 0;
        Map<Integer, Tarea> tareasCargadas = new HashMap<>();

        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(
                        new FileInputStream(ARCHIVO), StandardCharsets.UTF_8))) {

            String linea;
            while ((linea = br.readLine()) != null) {
                linea = linea.strip();
                if (linea.isEmpty() || linea.startsWith("#")) continue;

                String[] partes = splitCsv(linea);

                try {
                    switch (partes[0]) {
                        case "USUARIO" -> {
                            if (partes.length < 3) break;
                            String nombre = des(partes[1]);
                            String pass   = des(partes[2]);
                            Usuario u = new Usuario(nombre, pass);
                            if (partes.length >= 6) {
                                try {
                                    u.setRachaActual(Integer.parseInt(partes[3]));
                                    u.setMejorRacha(Integer.parseInt(partes[4]));
                                    if (!partes[5].equals("NULL")) {
                                        u.setUltimaFechaCompletada(LocalDate.parse(partes[5]));
                                    }
                                } catch (Exception ignored) {}
                            }
                            usuarios.put(nombre, u);
                        }
                        case "TAREA_SIMPLE" -> {
                            if (partes.length < 8) break;
                            String          dueno      = des(partes[1]);
                            int             id         = Integer.parseInt(partes[2]);
                            String          titulo     = des(partes[3]);
                            String          desc       = des(partes[4]);
                            Prioridad       prioridad  = Prioridad.valueOf(partes[5]);
                            Estado          estado     = Estado.valueOf(partes[6]);
                            LocalDateTime   fechaCrea  = LocalDateTime.parse(partes[7]);

                            Usuario u = usuarios.get(dueno);
                            if (u != null) {
                                Tarea t = tareasCargadas.get(id);
                                if (t == null) {
                                    t = new TareaSimple(id, titulo, desc, prioridad, fechaCrea);
                                    t.setEstado(estado);
                                    tareasCargadas.put(id, t);
                                }
                                u.agregarTarea(t);
                                if (id > maxId) maxId = id;
                            }
                        }
                        case "TAREA_FECHA" -> {
                            if (partes.length < 9) break;
                            String          dueno      = des(partes[1]);
                            int             id         = Integer.parseInt(partes[2]);
                            String          titulo     = des(partes[3]);
                            String          desc       = des(partes[4]);
                            Prioridad       prioridad  = Prioridad.valueOf(partes[5]);
                            Estado          estado     = Estado.valueOf(partes[6]);
                            LocalDateTime   fechaCrea  = LocalDateTime.parse(partes[7]);
                            LocalDate       fechaLim   = LocalDate.parse(partes[8]);

                            Usuario u = usuarios.get(dueno);
                            if (u != null) {
                                Tarea t = tareasCargadas.get(id);
                                if (t == null) {
                                    t = new TareaConFechaLimite(
                                            id, titulo, desc, prioridad, fechaCrea, fechaLim);
                                    t.setEstado(estado);
                                    tareasCargadas.put(id, t);
                                }
                                u.agregarTarea(t);
                                if (id > maxId) maxId = id;
                            }
                        }
                        case "SUBTAREA" -> {
                            if (partes.length < 5) break;
                            String  dueno      = des(partes[1]);
                            int     idPadre    = Integer.parseInt(partes[2]);
                            String  tituloSub  = des(partes[3]);
                            boolean completada = Boolean.parseBoolean(partes[4]);

                            Usuario u = usuarios.get(dueno);
                            if (u != null) {
                                Tarea t = tareasCargadas.get(idPadre);
                                if (t != null) {
                                    t.agregarSubtarea(new Subtarea(tituloSub, completada));
                                }
                            }
                        }
                    }
                } catch (Exception lineaCorrupta) {
                    System.err.println("  [AVISO] Línea ignorada por datos corruptos: " + linea);
                }
            }

        } catch (IOException e) {
            System.err.println("  [ERROR] No se pudo leer los datos: " + e.getMessage());
        }

        // Sincronizar el contador de IDs para evitar duplicados
        Tarea.resetContador(maxId + 1);
        return usuarios;
    }

    // ─── Utilidades de escape ─────────────────────────────────────────────

    /** Escapa los punto y coma dentro de un campo para no romper el CSV. */
    private static String esc(String s) {
        if (s == null) return "";
        return s.replace(";", "\\;");
    }

    /** Restaura los punto y coma escapados al leer un campo. */
    private static String des(String s) {
        if (s == null) return "";
        return s.replace("\\;", ";");
    }

    /**
     * Divide una línea CSV respetando los punto y coma escapados (\;).
     * Un '\;' dentro de un campo NO se trata como separador de columna.
     */
    private static String[] splitCsv(String linea) {
        List<String> campos = new ArrayList<>();
        StringBuilder actual = new StringBuilder();
        for (int i = 0; i < linea.length(); i++) {
            char c = linea.charAt(i);
            if (c == '\\' && i + 1 < linea.length() && linea.charAt(i + 1) == ';') {
                actual.append("\\;"); // es un punto y coma escapado → lo dejamos intacto
                i++;
            } else if (c == ';') {
                campos.add(actual.toString());
                actual.setLength(0);
            } else {
                actual.append(c);
            }
        }
        campos.add(actual.toString());
        return campos.toArray(new String[0]);
    }
}
