public class Subtarea {
    private String titulo;
    private boolean completada;

    public Subtarea(String titulo, boolean completada) {
        this.titulo = titulo;
        this.completada = completada;
    }

    public Subtarea(String titulo) {
        this(titulo, false);
    }

    public String getTitulo() {
        return titulo;
    }

    public void setTitulo(String titulo) {
        this.titulo = titulo;
    }

    public boolean isCompletada() {
        return completada;
    }

    public void setCompletada(boolean completada) {
        this.completada = completada;
    }

    public void alternar() {
        this.completada = !this.completada;
    }
}
