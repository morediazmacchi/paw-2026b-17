# plancito

Aplicación web de gestión de eventos varios en CABA: Spring MVC + JSP/JSTL en la capa web, JDBC puro para persistencia, PostgreSQL como base de datos.

## Credenciales

| Nivel de acceso | Email | Contraseña |
|---|---|---|
| Superadmin (incluye también admin y usuario común) | `admin@plancito.com` | `admin1234` |
| Usuario común | Cualquier cuenta nueva creada en `/registro` | la que elijas al registrarte |

Todo usuario que se registra arranca con permisos de usuario común (`ROLE_USER`). `admin@plancito.com` es la única cuenta superadmin que existe (se crea sola al levantar la base, ver `data.sql`); desde ahí, en `/admin/usuarios`, se le puede dar o sacar el rol admin a cualquier otra cuenta.

## Cómo levantar el proyecto en local

Necesitás Java 21, Maven y PostgreSQL 16.

```bash
# 1. Base local (una vez)
sudo -u postgres psql -c "CREATE USER paw WITH PASSWORD 'paw';"
sudo -u postgres psql -c "CREATE DATABASE paw2026b OWNER paw;"

# 2. Compilar (repetir si cambia model, persistence o services)
mvn clean install -DskipTests

# 3. Levantar
mvn -pl webapp jetty:run -Ddb.url=jdbc:postgresql://localhost/paw2026b -Ddb.username=paw -Ddb.password=paw
```

La app queda en **http://localhost:8080/**. Las tablas y los datos iniciales se crean solos al arrancar.

> `db.properties` apunta por defecto a la base de **Pampero**: sin los `-D...` del paso 3 la app escribe ahí.

## Generar el WAR

```bash
mvn clean package
```
Genera `webapp/target/app.war`, listo para deployar en un Application Container con PostgreSQL ya configurado.
