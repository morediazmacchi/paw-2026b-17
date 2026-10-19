# plancito

Aplicación web de gestión de eventos varios en CABA: Spring MVC + JSP/JSTL en la capa web, JDBC puro para persistencia, PostgreSQL como base de datos.

## Credenciales

| Nivel de acceso | Email | Contraseña |
|---|---|---|
| Superadmin (incluye también admin y usuario común) | `admin@plancito.com` | `admin1234` |
| Usuario común | Cualquier cuenta nueva creada en `/registro` | la que elijas al registrarte |

Todo usuario que se registra arranca con permisos de usuario común (`ROLE_USER`). `admin@plancito.com` es la única cuenta superadmin que existe (se crea sola al levantar la base, ver `data.sql`); desde ahí, en `/admin/usuarios`, se le puede dar o sacar el rol admin a cualquier otra cuenta.

## Correr la aplicación en desarrollo

La conexión a la base vive en `webapp/src/main/resources/db.properties` (`db.url`, `db.username`, `db.password`). Al arrancar, la app corre sola `schema.sql`, `postgres-migrations.sql` y `data.sql` sobre esa base (son idempotentes), así que no hay que crear tablas a mano.

Primero hay que instalar los módulos en el repositorio local de Maven (`jetty:run` sobre `webapp` los toma de ahí):

```bash
mvn clean install -DskipTests
```

### Con base local

Hace falta un Postgres local con una base `paw2026b` y un usuario `paw` (contraseña `paw`) que sea su dueño. Una sola vez, como superusuario de Postgres:

```sql
CREATE USER paw WITH PASSWORD 'paw';
CREATE DATABASE paw2026b OWNER paw;
```

Después, sin tocar ningún archivo, se pisa la conexión por línea de comandos (las propiedades `-D` ganan sobre `db.properties`):

```bash
mvn -pl webapp jetty:run \
  -Ddb.url=jdbc:postgresql://localhost/paw2026b \
  -Ddb.username=paw -Ddb.password=paw
```

La app queda en <http://localhost:8080>. Alternativa: en `db.properties`, comentar el bloque `PAMPERO` y descomentar el bloque `LOCAL`, y correr solo `mvn -pl webapp jetty:run` (acordate de no commitear ese cambio).

### Con la base de Pampero

Es lo que trae `db.properties` tal cual está commiteado: sin ningún `-D`, la app se conecta a la base de Pampero.

```bash
mvn -pl webapp jetty:run
```

La IP de la base (`10.16.1.110`) es interna, así que solo responde desde la red del ITBA (o por VPN). Ojo: es la base compartida del equipo y la app le corre `schema.sql` y las migraciones al arrancar; no es un entorno descartable.

### Si `jetty:run` falla con "Unsupported class file major version"

Jetty 9 no sabe leer las clases compiladas para Java 25. Para correr en desarrollo hay que compilar para Java 21, agregando estos tres `-D` tanto al `mvn clean install` como al `mvn -pl webapp jetty:run` (el segundo recompila `webapp`):

```bash
-Dmaven.compiler.source=21 -Dmaven.compiler.target=21 -Dmaven.compiler.release=21
```

## Generar el WAR

Requiere JDK 25 (LTS).

```bash
mvn clean package
```
Genera `webapp/target/app.war`, listo para deployar en un Application Container con PostgreSQL ya configurado.
