package com.dssd.backend.config;

import com.dssd.backend.repositories.EvaluacionConvocatoriaRepository;
import com.dssd.backend.services.ArchivoEvaluacionService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Migración aditiva: conserva el histórico antes de vaciar las copias JSON antiguas. */
@Component
public class MigracionEvaluaciones implements ApplicationRunner {
    private final EvaluacionConvocatoriaRepository evaluaciones;
    private final ArchivoEvaluacionService archivo;
    private final JdbcTemplate jdbc;
    public MigracionEvaluaciones(EvaluacionConvocatoriaRepository evaluaciones, ArchivoEvaluacionService archivo, JdbcTemplate jdbc) {
        this.evaluaciones = evaluaciones; this.archivo = archivo; this.jdbc = jdbc;
    }
    @Override @Transactional
    public void run(ApplicationArguments args) {
        // Hibernate update no elimina siempre los NOT NULL del esquema anterior.
        for (String columna : new String[]{"datos_json", "resultado_json", "validaciones_json"}) {
            var nullable = jdbc.queryForObject("SELECT is_nullable FROM information_schema.columns "
                    + "WHERE lower(table_name) = 'evaluacion_convocatoria' AND lower(column_name) = ?", String.class, columna);
            if ("NO".equalsIgnoreCase(nullable))
                jdbc.execute("ALTER TABLE evaluacion_convocatoria ALTER COLUMN " + columna + " DROP NOT NULL");
        }
        for (var ev : evaluaciones.findAll()) {
            if (ev.getUmbralPorcentaje() == null) archivo.guardar(ev, archivo.leer(ev));
        }
        evaluaciones.flush();
    }
}
