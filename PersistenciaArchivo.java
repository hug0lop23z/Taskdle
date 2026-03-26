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
                bw.write("USUARIO" + SEP + esc(u.getNombreUsuario()) + SEP + esc(u.getContrasena()));
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

        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(
                        new FileInputStream(ARCHIVO), StandardCharsets.UTF_8))) {

            String linea;
            while ((linea = br.readLine()) != null) {
                linea = linea.strip();
                if (linea.isEmpty() || linea.startsWith("#")) continue;

                String[] partes = linea.split(SEP, -1);

                switch (partes[0]) {
                    case "USUARIO" -> {
                        if (partes.length < 3) break;
                        String nombre = des(partes[1]);
                        String pass   = des(partes[2]);
                        usuarios.put(nombre, new Usuario(nombre, pass));
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
                            TareaSimple ts = new TareaSimple(id, titulo, desc, prioridad, fechaCrea);
                            ts.setEstado(estado);
                            u.agregarTarea(ts);
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
                            TareaConFechaLimite tcfl = new TareaConFechaLimite(
                                    id, titulo, desc, prioridad, fechaCrea, fechaLim);
                            tcfl.setEstado(estado);
                            u.agregarTarea(tcfl);
                            if (id > maxId) maxId = id;
                        }
                    }
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
}
