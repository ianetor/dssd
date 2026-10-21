package com.dssd.backend.services;

/** Separa el sufijo de presentación que el formulario agrega al tipo de recurso. */
public final class NombreRecursoNacional {
    private NombreRecursoNacional() {}

    public static String obtener(String tipoRecurso, String unidad) {
        String nombre = tipoRecurso.trim();
        if (unidad == null || unidad.isBlank()) return nombre;
        String sufijo = " (" + unidad.trim() + ")";
        if (nombre.length() > sufijo.length()
                && nombre.regionMatches(true, nombre.length() - sufijo.length(), sufijo, 0, sufijo.length())) {
            return nombre.substring(0, nombre.length() - sufijo.length()).trim();
        }
        return nombre;
    }
}
