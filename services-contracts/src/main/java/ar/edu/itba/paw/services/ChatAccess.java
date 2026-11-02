// Que se le ofrece a una persona del grupo de chat de un evento, ya decidido por el service.
package ar.edu.itba.paw.services;

public enum ChatAccess {
    // Nada: no esta anotada con lugar confirmado, o el evento ya no esta vigente y todavia no se habia unido.
    NONE,
    // Ya es miembro: ve el acceso al chat aunque el evento haya terminado o se haya cancelado (solo lectura).
    OPEN,
    // Tiene el lugar confirmado, todavia no es miembro y el evento sigue vigente: puede unirse.
    JOIN
}
