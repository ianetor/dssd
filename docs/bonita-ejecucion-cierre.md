# Bonita: ejecución, cierre y liberación de recursos

Cambios a aplicar en Bonita Studio sobre el diagrama `MiDiagrama-1.0.proc` (pool `RescueSync`) para el tramo final
del proceso. El backend y el front de esta etapa ya están en la rama `feature/ejecucion-cierre` de `dssd`.

## Cómo queda el tramo final

```
Registrar Compromiso Nacional      (servicio, ya hecho: bloquea en el Sistema Nacional)
        ↓
Notificar a ONGs adjudicadas       (servicio)  → avisa al backend local: empieza la ejecución
        ↓
Monitorear despliegue y ejecucion  (humana, Centro Coordinador) → se completa desde la app web
        ↓
Cierre y liberacion de recursos    (pasa a ser de servicio) → login + cierre nacional + aviso local
        ↓
¿Termino la emergencia?  →  Fin de emergencia
```

Las ONGs no tienen tarea en Bonita en esta etapa: marcan "actividades finalizadas" en la app web
(`/portal-ong/adjudicaciones`) y eso se guarda en la base local.

## 0. Antes de empezar

1. Actualizar el repo de Bonita. La copia local puede estar atrasada respecto de GitHub:
   ```bash
   git stash
   git pull
   ```
   (`git stash` guarda los `.project` que Studio modifica solo; no hace falta recuperarlos.)
2. Abrir el proyecto en Studio y el diagrama `MiDiagrama`.
3. No hay que crear variables ni parámetros: se reutilizan los que ya existen
   (`urlBackend`, `claveIntegracionBackend`, `urlSistemaNacional`, `usuarioSistemaNacional`,
   `passwordSistemaNacional`, `tokenSistemaNacional`, `emergenciaId`).

> En los scripts Groovy conviene no usar tildes ni ñ en los textos: el diagrama ya tiene mensajes con la
> codificación rota por eso.

## 1. Notificar a ONGs adjudicadas

Seleccionar la tarea → pestaña **Execution → Connectors in → Add** → **REST → POST**.

| Campo | Valor |
|---|---|
| Name | `iniciarEjecucionLocal` |
| URL (editar como **Script**) | `return urlBackend + "/api/integracion/casos/" + rootProcessInstanceId + "/ejecucion/iniciar"` |
| Content type | `application/json` |
| Headers | `X-Integration-Key` = parámetro `claveIntegracionBackend` |
| Payload | `{}` |
| Authentication | None |
| Fail on 4xx / 5xx | **marcados** |
| Output operations | ninguna |

Qué hace el backend al recibirlo: pasa la emergencia a `EN_EJECUCION`, marca las ofertas activas como
`recursosBloqueados` y crea una participación por ONG. Eso es lo que cada ONG ve como notificación de adjudicación.
Es idempotente: si Bonita reintenta el conector no se duplica nada.

## 2. Monitorear despliegue y ejecucion

No lleva conectores. Solo hay que revisar tres cosas:

- **Nombre exacto:** `Monitorear despliegue y ejecucion` (sin tilde). El backend busca la tarea por ese nombre
  (`BonitaService.TAREA_MONITOREO`).
- **Contrato vacío:** pestaña **Execution → Contract** sin entradas. El backend rechaza el cierre si el contrato
  tiene inputs.
- **Sin formulario:** pestaña **Execution → Form → No form**. La tarea se completa desde la app web
  (`/coordinador/ejecucion/{id}`), no desde el portal de Bonita.

El actor queda como está: lane **Centro Coordinador**.

## 3. Cierre y liberacion de recursos

### 3.1 Convertirla en tarea de servicio
Seleccionar la tarea → pestaña **General → Task type → Service**. Opcional: arrastrarla al lane **Red Nacional**
para que el dibujo sea coherente (ya no la ejecuta una ONG).

### 3.2 Conectores (los tres en *Connectors in*, **en este orden**)

**a) `loginCierreNacional` — REST POST.** Hace falta volver a autenticar: el JWT dura 60 minutos y la ejecución en
terreno dura más.

| Campo | Valor |
|---|---|
| URL (Script) | `return urlSistemaNacional.trim().replaceAll('/+$', '') + "/api/auth/login"` |
| Content type | `application/json` |
| Payload (Script) | ver abajo |
| Authentication | None |
| Fail on 4xx / 5xx | marcados |
| Output operation | `tokenSistemaNacional` **takes value of** el script de abajo |

Payload:
```groovy
import groovy.json.JsonOutput
return JsonOutput.toJson([username: usuarioSistemaNacional, password: passwordSistemaNacional])
```

Script de la operación de salida (tipo de retorno `java.lang.String`):
```groovy
import groovy.json.JsonSlurper
if (!bodyAsString?.trim()) {
    throw new IllegalStateException("La respuesta del login esta vacia")
}
def json = new JsonSlurper().parseText(bodyAsString)
if (!json.accessToken?.toString()?.trim()) {
    throw new IllegalStateException("La respuesta no contiene accessToken")
}
return json.accessToken.toString().trim()
```

**b) `cierreNacional` — REST POST.** Libera en el Sistema Nacional todos los recursos del proyecto.

| Campo | Valor |
|---|---|
| URL (Script) | `return urlSistemaNacional.trim().replaceAll('/+$', '') + "/api/proyectos/" + emergenciaId + "/cierre"` |
| Content type | `application/json` |
| Payload | `{}` |
| Authentication | **OAuth2 (Bearer)**, token (Script): `return tokenSistemaNacional` |
| Fail on 4xx / 5xx | marcados |
| Output operations | ninguna |

El `proyectoRef` es `emergenciaId`, el mismo que usa `registroCompromiso` al bloquear. Si no coinciden, el Sistema
Nacional responde 404 ("No hay compromisos registrados para el proyecto").

**c) `cerrarEjecucionLocal` — REST POST.** Avisa al backend local que los recursos ya fueron liberados.

| Campo | Valor |
|---|---|
| URL (Script) | `return urlBackend + "/api/integracion/casos/" + rootProcessInstanceId + "/ejecucion/cerrar"` |
| Content type | `application/json` |
| Headers | `X-Integration-Key` = parámetro `claveIntegracionBackend` |
| Payload | `{}` |
| Authentication | None |
| Fail on 4xx / 5xx | marcados |

El orden importa: la emergencia local solo pasa a `FINALIZADA` después de que el Sistema Nacional confirmó la
liberación. Si un conector falla, la tarea queda en *failed* y se puede reintentar desde el portal de Bonita: los tres
servicios son idempotentes (repetir el cierre nacional no libera dos veces).

## 4. Compuerta "¿Termino la emergencia?"

No hace falta tocarla: el flujo por defecto ya va a **Fin de emergencia**. El otro flujo (vuelve a *Registrar
Compromiso Nacional* con condición `return false`) nunca se toma; se puede borrar para limpiar el diagrama.

## 5. Probar el circuito completo

1. Levantar el Sistema Nacional (`sistema-nacional`, puerto 8090), `dssd` (8080 / 4200) y desplegar el proceso.
2. Llevar una emergencia hasta cobertura suficiente. Tras *Registrar Compromiso Nacional*:
   - En la base nacional, `recurso_ong.cantidad_bloqueada` sube y `compromiso.estado = 'ACTIVO'`.
   - En la app, la emergencia figura **EN EJECUCION** y cada ONG ve la adjudicación en *Mis adjudicaciones*.
3. Con cada usuario ONG: **Marcar actividades como finalizadas**.
4. Con el coordinador: *Monitorear ejecución* → **Cerrar ejecución** (o **Forzar cierre** con motivo si queda alguna ONG
   en curso). La emergencia pasa a **CIERRE PENDIENTE**.
5. Bonita ejecuta *Cierre y liberacion de recursos*. Verificar:
   - Base nacional: `cantidad_bloqueada` vuelve a 0 y `compromiso.estado = 'LIBERADO'`.
   - App: emergencia **FINALIZADA**; caso de Bonita terminado.

## Endpoints del backend local usados en esta etapa

| Quién | Método y ruta | Acceso |
|---|---|---|
| Bonita | `POST /api/integracion/casos/{caseId}/ejecucion/iniciar` | `X-Integration-Key` |
| Bonita | `POST /api/integracion/casos/{caseId}/ejecucion/cerrar` | `X-Integration-Key` |
| Coordinador / Auditor | `GET /api/coordinador/emergencias/{id}/ejecucion` | sesión |
| Coordinador | `POST /api/coordinador/emergencias/{id}/ejecucion/cierre` (`{forzar, motivo}`) | sesión |
| ONG | `GET /api/ejecucion/adjudicaciones` | `X-ONG-Username` |
| ONG | `POST /api/ejecucion/emergencias/{id}/finalizacion` (`{observaciones}`) | `X-ONG-Username` |

## Observaciones sobre lo que ya estaba

- **Credenciales del Sistema Nacional:** el login actual manda la contraseña `bonita123456`. Tiene que coincidir con
  el `SISTEMA_PASSWORD` con que se levantó el Sistema Nacional; si no, el login devuelve 401.
- **`registroCompromiso`:** el script del *body* ya hace los `POST /api/recursos/bloqueos` por su cuenta y después el
  conector vuelve a enviar a esa misma URL la lista de resultados, que el Sistema Nacional rechaza con 400. No rompe
  nada porque el conector tiene *Fail on 4xx* desmarcado, pero es una llamada de más y oculta errores reales.
