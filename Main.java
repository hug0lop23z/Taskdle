/**
 * Punto de entrada de la aplicación.
 * Lanza el servidor web en http://localhost:8080 y abre el navegador.
 *
 * Para compilar y ejecutar:
 *   javac *.java
 *   java Main
 *
 * Para usar la versión de consola, reemplaza la línea de abajo por:
 *   new MenuPrincipal().iniciar();
 */
public class Main {
    public static void main(String[] args) throws Exception {
        ServidorWeb.iniciar();
    }
}
