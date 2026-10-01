# Etapa 1: publicación y plazo persistidos

Esta etapa guarda la duración y las fechas, publica mediante una operación recuperable y elimina el éxito simulado. **Todavía no configura el timer de Bonita, no muestra cuenta regresiva ni cierra por vencimiento/cobertura.** El timer existente en el diagrama sigue en 9 segundos. No hay que editar ni redesplegar el proceso para esta etapa.

## Preparación

1. Reiniciar backend y frontend con el código actualizado. En Docker: `docker compose up -d --build backend frontend`.
2. Hibernate agrega las nuevas columnas a PostgreSQL mediante el `ddl-auto=update` existente. No se borran datos.
3. Tener desplegado RescueSync 1.0 en Bonita y usar una emergencia **nueva** con estado REGISTRADA, sin lotes. Los casos anteriores no reciben un plazo inventado ni se pueden republicar.
4. Registrar la emergencia con Bonita encendido; la prueba de desconexión se hace después de registrarla.

## A. Publicación normal

1. Como coordinador, definir al menos un lote y elegir 2 horas.
2. Publicar: se muestra `Publicación pendiente`. El POST devuelve 202, con duración de 120 minutos.
3. En unos segundos pasa a convocatoria publicada si el usuario configurado en el backend puede ejecutar `Revisar Emergencia y Generar Lotes`.
4. El detalle muestra inicio y vencimiento registrados. La diferencia debe ser exactamente 120 minutos.
5. Recargar: se conservan duración, fechas y lotes; no se puede editar ni volver a publicar.
6. Consultar `GET /api/emergencias/{id}`: debe conservar las fechas ISO 8601 UTC, estado y duración. `horaServidor` cambia en cada respuesta.

No evaluar el cierre automático en esta etapa: Bonita todavía usa su timer anterior y el backend todavía no sincroniza ese cierre.

## B. Validaciones

En otra emergencia nueva, probar 0, -1, 1.5 y un campo vacío: no se debe poder publicar. Una duración de 1 minuto sí es válida. Sin lotes, el botón también queda deshabilitado.

La API rechaza con 400 duración nula, fraccionaria o fuera de 1..2147483647 minutos, lotes vacíos/nulos, nombres vacíos y cantidades no positivas. El límite técnico evita desbordamientos de conversión; no restringe a las antiguas opciones de 4/8/12/24 horas.

## C. Duplicados y conflictos

Desde Network del navegador, repetir el POST original con el mismo cuerpo:

```json
{
  "duracionMinutos": 15,
  "lotes": [{ "tipoRecurso": "Agua (litros)", "cantidadRequerida": 100 }]
}
```

Debe devolver el mismo contenido persistido (202 pendiente o 200 publicada), sin nuevos lotes ni fechas nuevas. Cambiar duración o contenido de lotes en esa repetición devuelve 409. El orden de los lotes no afecta a la comparación.

## D. Bonita desconectado y recuperación

1. Registrar otra emergencia y luego detener Bonita.
2. Publicar desde el front. La solicitud queda guardada y pendiente; no aparece éxito.
3. Tras el intento fallido, se muestra un mensaje de reintento. Consultar la API y anotar las fechas.
4. Recargar el navegador y reiniciar el backend. La solicitud permanece pendiente, con las mismas fechas y lotes.
5. Volver a iniciar Bonita **sin borrar ni reinstanciar el caso**. La publicación se recupera automáticamente.
6. Confirmar que hay una única ejecución de la tarea de publicación y que las fechas no cambiaron.

El worker revisa pendientes cada 5 segundos y reintenta fallos tras 15 segundos. Si el backend se interrumpe durante un intento, la reserva persistida puede retrasar la recuperación hasta 120 segundos. El plazo empieza en el primer intento, incluso si Bonita está desconectado; los reintentos no lo extienden.

Mientras esté PUBLICACION_PENDIENTE, la API rechaza crear, modificar y retirar ofertas con 409. El listado de convocatorias abiertas no debe incluirla.

## Pruebas automatizadas

- Backend: `mvn -Dtest=PublicacionConvocatoriaTest,BonitaPublicacionTest test` desde `backend`.
- Frontend: `npm test -- --watch=false --include=src/app/components/emergencia-detail/emergencia-detail.component.spec.ts` desde `frontend`.
- Compilación frontend: `npm run build`.

Las pruebas de publicación usan H2 y Bonita simulado: verifican persistencia, duplicados concurrentes, validaciones y reintentos sin modificar tus casos reales. La prueba manual comprueba la conexión y permisos de tu Bonita local.

Para aislar pruebas automáticas puede desactivarse el worker con `convocatoria.publicacion.worker-enabled=false`. En uso normal queda habilitado por defecto.
