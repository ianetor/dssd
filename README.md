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

## Verificaciones Realizadas

1. **Backend**:
   - `mvn clean compile`: Compiló exitosamente el código fuente.
   - `mvn test`: Ejecutó los tests de inicio de contexto de Spring Boot con resultado **BUILD SUCCESS** (0 fallas, 0 errores).
2. **Frontend**:
   - `npm install`: Instaló correctamente todos los paquetes y dependencias en `node_modules`.
   - `npm run build`: Generó el bundle de producción exitosamente (`dist/frontend`) en 20 segundos sin errores.
3. **Control de Versiones (Git)**:
   - Verificado con `git status` que ni `backend/target/` ni `frontend/node_modules/` o `frontend/dist/` están trackeados por git.
