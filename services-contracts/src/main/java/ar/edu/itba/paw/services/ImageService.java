package ar.edu.itba.paw.services;

import ar.edu.itba.paw.model.EventAccessSummary;
import ar.edu.itba.paw.model.Image;
import ar.edu.itba.paw.model.exception.InvalidImageException;

import java.util.Optional;

public interface ImageService {

    /**
     * Guarda una imagen nueva. Recibe los bytes crudos y no un MultipartFile a proposito:
     * MultipartFile es de la capa web y no debe filtrarse a la capa de servicios.
     *
     * @return la imagen guardada, ya con su id asignado por la base
     * @param contentType lo que declaro el cliente: no se le cree. Solo se usa como primer
     *                    filtro; el tipo que se guarda es el que salga de mirar los bytes.
     * @throws InvalidImageException si el archivo esta vacio, pesa demasiado, o sus bytes no
     *                               son un JPEG, PNG, GIF o WebP (SVG y demas, nunca)
     */
    Image create(byte[] data, String contentType, String fileName);

    Optional<Image> findById(long id);

    /**
     * Los datos del evento al que pertenece la imagen que alcanzan para decidir si quien la pide
     * puede verla: una imagen hereda la visibilidad de su evento. Vacio si no cuelga de ninguno.
     */
    Optional<EventAccessSummary> findOwnerEventAccess(long imageId);
}
