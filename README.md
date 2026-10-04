# plancito

Aplicación web de gestión de eventos varios en CABA: Spring MVC + JSP/JSTL en la capa web, JDBC puro para persistencia, PostgreSQL como base de datos.

## Credenciales

| Nivel de acceso | Email | Contraseña |
|---|---|---|
| Superadmin (incluye también admin y usuario común) | `admin@plancito.com` | `admin1234` |
| Usuario común | Cualquier cuenta nueva creada en `/registro` | la que elijas al registrarte |

Todo usuario que se registra arranca con permisos de usuario común (`ROLE_USER`). `admin@plancito.com` es la única cuenta superadmin que existe (se crea sola al levantar la base, ver `data.sql`); desde ahí, en `/admin/usuarios`, se le puede dar o sacar el rol admin a cualquier otra cuenta.

## Cómo levantar el proyecto en local

### Necesitás tener instalado
- Java 21
- Maven (o el wrapper `./mvnw` que ya viene en el repo, no hace falta instalar nada aparte)
- PostgreSQL 16, con permisos y credenciales adecuadas

### Generar el WAR

Desde la raíz del repo:
```bash
mvn clean package
```
Esto genera `webapp/target/app.war`, listo para deployar en un Application Container con PostgreSQL ya configurado.
