# Resumen de Inicialización: Frontend (Angular) y Backend (Spring Boot)

Se inicializaron con éxito los proyectos de **Frontend** y **Backend** en directorios separados dentro del repositorio, junto con la descarga de dependencias, configuración de CORS, base de datos H2/PostgreSQL y archivos `.gitignore` específicos.

---

## 1. Backend (`backend/`)

- **Framework**: Spring Boot 3.4.3
- **Java**: 17 LTS
- **Build tool**: Maven con Maven Wrapper (`mvnw` y `mvnw.cmd`)
- **Dependencias instaladas y verificadas**:
  - `spring-boot-starter-web`: Creación de endpoints y controladores REST.
  - `spring-boot-starter-data-jpa`: Hibernate ORM, repositorios y persistencia.
  - `spring-boot-starter-validation`: Validaciones Jakarta (`@NotNull`, `@NotBlank`, etc.).
  - `h2`: Base de datos en memoria lista para desarrollo inmediato con consola web habilitada en `/h2-console`.
  - `postgresql`: Driver para conexión a PostgreSQL.
  - `lombok`: Reducción de boilerplate en entidades y DTOs (`@Getter`, `@Setter`, etc.).
  - `spring-boot-devtools`: Recarga rápida en desarrollo.
  - `spring-boot-starter-test`: JUnit 5, AssertJ, Mockito.
- **Configuración de CORS**: Implementada en [CorsConfig.java](file:///c:/Users/tobias.pena/Documents/Distribuidos/dssd/backend/src/main/java/com/dssd/backend/config/CorsConfig.java) permitiendo peticiones desde `http://localhost:4200`.
- **Configuración de Propiedades**: [application.properties](file:///c:/Users/tobias.pena/Documents/Distribuidos/dssd/backend/src/main/resources/application.properties) configurado en el puerto `8080` con H2 habilitado.
- **Gitignore**: [backend/.gitignore](file:///c:/Users/tobias.pena/Documents/Distribuidos/dssd/backend/.gitignore) ignora `target/`, metadatos de IDEs y logs.

### Comandos de ejecución (Backend)
```powershell
cd backend
./mvnw spring-boot:run
```

---

## 2. Frontend (`frontend/`)

- **Framework**: Angular 21 (versión más reciente compatible con el motor Node.js v22.18 instalado en el sistema).
- **Estilos**: SCSS (`.scss`).
- **Routing**: Habilitado (`app.routes.ts`).
- **Arquitectura**: Componentes Standalone.
- **HTTP Client**: Configurado con `provideHttpClient(withFetch())` en [app.config.ts](file:///c:/Users/tobias.pena/Documents/Distribuidos/dssd/frontend/src/app/app.config.ts) para el consumo inmediato de las APIs del backend.
- **Gitignore**: [frontend/.gitignore](file:///c:/Users/tobias.pena/Documents/Distribuidos/dssd/frontend/.gitignore) ignora `/node_modules`, `/dist`, `/.angular`, `.env` y temporales.

### Comandos de ejecución (Frontend)
```powershell
cd frontend
npm start
```
(Acceso en el navegador a `http://localhost:4200`).

---

## 3. Integración con Bonita (RescueSync)

Este proyecto no contiene el proyecto Bonita: se integra contra un **Bonita Studio** que corre en cada máquina de desarrollo, en un repo aparte.

### ¿Cómo correr Bonita?

1. Abrir el proyecto Bonita en **Bonita Studio** (rebuild de la máquina).
2. Configurar el puerto del servidor embebido: `Preferences` → `Server` → **Port number = 8081** (no usar 8080, lo usa el backend). Requiere reiniciar el Studio.
3. El botón **Run** desplega el proceso en el motor embebido. **Ojo**: el botón aparece gris si el servidor de Bonita no arrancó. Si pasa:
   - Cerrar el Studio por completo y volver a abrirlo (el cambio de puerto toma efecto al reiniciar).
   - Verificar el indicador del servidor embebido (círculo verde/rojo en la barra).
   - Tener el diagrama abierto y seleccionado en el editor.
   - Revisar la validación del proceso (errores deshabilitan el Run).
   - Logs: `Help` → `Show Bonita Studio log`.
4. Con el proceso corriendo, Bonita responde en `http://localhost:8081/bonita`.

`host.docker.internal` resuelve el host desde los contenedores Docker

### Endpoints disponibles

Todos bajo `http://localhost:8080/api/bonita`:

| Método | Ruta | Descripción |
|---|---|---|
| GET | `/health` | Estado de conexión/login con Bonita |
| GET | `/procesos` | Procesos en el motor Bonita |
| GET | `/tareas` | Tareas pendientes |
| GET | `/casos` | Casos (instancias de proceso) |
| GET | `/usuarios` | Usuarios de Bonita |
| GET | `/recurso/{recurso}?p=0&c=100` | Passthrough genérico a cualquier recurso `/API/{recurso}` de Bonita |

Los endpoints de recursos devuelven el **JSON crudo** de Bonita como `text/plain`.

### Uso desde el frontend

`src/app/helpers/environment.ts` define `apiUrl`. Consumir siempre vía el backend, nunca directo a Bonita:

```ts
import { BonitaService } from '../services/bonita.service';
// this.bonita.procesos().subscribe(json => ...);
```

---
