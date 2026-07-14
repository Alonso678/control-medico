# 🩺 Manual de Instalación y Despliegue - Control Médico Familiar

Este documento contiene las especificaciones técnicas, requerimientos de entorno, arquitectura y pasos detallados para configurar, inicializar y desplegar de manera exitosa el sistema de **Control Médico Familiar** en un entorno de desarrollo local.

---

## 🏗️ 1. Arquitectura del Sistema

El proyecto está diseñado bajo una arquitectura limpia y desacoplada basada en el patrón **Modelo-Vista-Controlador (MVC)** asistido por **Spring Boot**, orientada a una estructura relacional monolítica robusta.

* **Capa de Presentación (Vista):** Renderizado dinámico en el servidor utilizando **Thymeleaf**, integrado con componentes responsivos y fragmentos de seguridad para la manipulación de accesos en el Front-End.
* **Capa de Controladores (Controlador):** Gestión de peticiones HTTP, mapeo de rutas (`/familia`, `/login`, etc.) y flujos de redirección de negocio.
* **Capa de Negocio / Servicios:** Orquestación de la lógica interna, validaciones de seguridad analíticas y control transaccional (`@Transactional`).
* **Capa de Datos (Modelo/Persistencia):** Mapeo de objetos relacionales mediante **JPA / Hibernate** que interactúa directamente con la base de datos mediante repositorios automatizados.
* **Seguridad Transversal:** Capa intermedia administrada por **Spring Security** para la autenticación y el control de accesos basados en roles dinámicos extraídos de la base de datos.

---

## 🛠️ 2. Tecnologías Utilizadas

El Stack tecnológico principal del proyecto está compuesto por:

* **Backend & Framework:** Java 17 / Spring Boot 3.x+ (utilizando Spring Web MVC, Spring Security y Spring Data JPA).
* **Gestor de Dependencias:** Maven 3.x (a través del componente integrado Maven Wrapper `./mvnw`).
* **Motor de Plantillas (Frontend):** Thymeleaf 3 con extensiones nativas para Spring Security 6.
* **Base de Datos:** PostgreSQL 16.x (Motor relacional).
* **Librerías Auxiliares:** * *Project Lombok:* Automatización de código repetitivo (Getters, Setters, Contructores).
    * *OpenPDF (v2.0.2):* Motor de generación y renderizado de documentos PDF integrados.

---

## 📋 3. Requisitos Previos del Sistema

Antes de iniciar la instalación, asegúrate de contar con las siguientes herramientas instaladas y configuradas en tu sistema operativo:

1. **Java Development Kit (JDK):** Versión 17 de manera estricta (Herramienta sugerida para administración: **SDKMAN!**).
2. **Motor de Base de Datos:** PostgreSQL instalado y corriendo localmente en el puerto estándar `5432`.
3. **Gestor de BD Gráfico (Opcional):** pgAdmin 4 o DBeaver para la correcta administración y ejecución de scripts.
4. **Sistema Operativo:** Compatible con entornos Linux (Ubuntu/Debian preferido), macOS o Windows (vía PowerShell).

---

## 🗄️ 4. Configuración y Preparación de la Base de Datos

El sistema opera bajo una base de datos relacional dedicada que incluye control estricto de accesos mediante un catálogo de roles.

### Especificaciones del Esquema:
* **Base de Datos:** `controlMedico_DB`
* **Esquema:** `control_medico`
* **Tablas Principales:** `roles`, `familiares`

### Paso 1: Inicialización de la Base de Datos y Roles
Abre tu consola de PostgreSQL (o pgAdmin) y ejecuta el siguiente bloque SQL para estructurar el esquema, la tabla de roles e insertar las credenciales maestras del sistema:

```sql
-- Asegurar la existencia del esquema dedicado
CREATE SCHEMA IF NOT EXISTS control_medico;

-- 1. Crear la tabla de catálogos de roles
CREATE TABLE control_medico.roles (
    id SERIAL PRIMARY KEY,
    descripcion VARCHAR(50) NOT NULL UNIQUE,
    fecha_creacion TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    fecha_modificacion TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    estatus BOOLEAN DEFAULT TRUE
);

-- 2. Insertar los roles maestros requeridos por Spring Security
INSERT INTO control_medico.roles (id, descripcion, estatus) VALUES 
(1, 'SYS_ADMIN', TRUE),
(2, 'ROLE_ADMIN', TRUE),
(3, 'ROLE_FAMILIAR', TRUE);

-- Ajustar el correlativo automático de la secuencia de IDs
SELECT setval('control_medico.roles_id_seq', 3);
Paso 2: Importar Datos Existentes (Respaldo del Repositorio)
Si deseas restaurar la estructura completa con datos históricos guardados en el repositorio del proyecto, puedes ejecutar el script de respaldo localizable en la carpeta del proyecto ejecutando:

Bash
# Ubicación del archivo en el repositorio: /DB/script_inicial.sql
psql -U postgres -d controlMedico_DB -f ./DB/script_inicial.sql
🚀 5. Pasos para Levantar el Proyecto en Desarrollo
Sigue esta secuencia de comandos en tu terminal local para compilar e iniciar la aplicación web:

Paso 1: Configurar el Entorno Correcto de Java (Vía SDKMAN!)
Dado que el proyecto exige rigurosamente Java 17 (versión de clase 61.0), utilizaremos SDKMAN! para aislar el entorno sin alterar configuraciones globales del sistema:

Bash
# 1. Asegurar la instalación de Java 17 (Temurin o la distribución de tu preferencia)
sdk install java 17.0.10-tem

# 2. Inicializar el archivo de entorno en la raíz del proyecto (Generará el archivo .sdkmanrc)
sdk env init

# 3. Activar el entorno de Java definido para la carpeta actual
sdk env
(Tip: Configura sdkman_auto_env=true en tu archivo global ~/.sdkmanrc para que este cambio de entorno sea automático al hacer cd hacia el proyecto).

Paso 2: Configurar las Propiedades del Proyecto
Verifica que las credenciales de tu archivo de propiedades localizado en src/main/resources/application.properties coincidan con tu entorno de PostgreSQL local:

Properties
spring.datasource.url=jdbc:postgresql://localhost:5432/controlMedico_DB?currentSchema=control_medico
spring.datasource.username=tu_usuario_postgres
spring.datasource.password=tu_contraseña_postgres
spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=true
Paso 3: Limpiar y Compilar el Proyecto (Maven)
Para eliminar temporales previos y compilar las clases (incluyendo el procesamiento de anotaciones de Lombok), ejecuta:

Bash
# En sistemas Linux / macOS:
./mvnw clean compile

# En sistemas Windows (PowerShell):
.\mvnw clean compile
Paso 4: Levantar el Servidor de Aplicaciones
Una vez compilado de manera exitosa, ejecuta el plugin oficial de Spring Boot para encender el servidor embebido Tomcat:

Bash
# En sistemas Linux / macOS:
./mvnw spring-boot:run

# En sistemas Windows (PowerShell):
.\mvnw spring-boot:run
Cuando observes el mensaje de confirmación Started ControlmedicoApplication in X seconds en tus logs, la aplicación estará lista.

🌐 6. Acceso al Sistema y Roles por Defecto
Abre tu navegador web e ingresa a la siguiente URL local:
👉 http://localhost:8080

Reglas Importantes de Autenticación Local:
Primer Registro Automático: Al levantar el proyecto por primera vez en una base de datos vacía, el primer usuario familiar que se registre a través del formulario web adquirirá de manera automática el rol maestro de ROLE_ADMIN.

Usuarios Subsecuentes: Todos los demás usuarios creados posteriormente ingresarán bajo el rol restrictivo de ROLE_FAMILIAR.

Seguridad de Cierre de Sesión: Cuando un Administrador herede su puesto de manera voluntaria a otro miembro mediante la ruta de control, el sistema forzará de inmediato una redirección hacia /login?logout=true para invalidar la sesión antigua y reestructurar los permisos en la base de datos de forma segura.
