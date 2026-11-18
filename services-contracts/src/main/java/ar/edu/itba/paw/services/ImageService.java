package ar.edu.itba.paw.services;

import ar.edu.itba.paw.model.EventAccessSummary;
import ar.edu.itba.paw.model.Image;
import ar.edu.itba.paw.model.exception.InvalidImageException;

import java.util.Optional;

public interface ImageService {

    /** Lo maximo que puede pesar cada foto de un evento: 5 MB. */
    int MAX_EVENT_IMAGE_BYTES = 5 * 1024 * 1024;

    /** Lo maximo que puede pesar la portada del perfil: 100 MB. */
    int MAX_COVER_BYTES = 100 * 1024 * 1024;

    /**
     * Guarda una imagen nueva. Recibe los bytes crudos y no un MultipartFile a proposito:
     * MultipartFile es de la capa web y no debe filtrarse a la capa de servicios.
     *
     * @return la imagen guardada, ya con su id asignado por la base
     * @param contentType lo que declaro el cliente: no se le cree. Solo se usa como primer
     *                    filtro; el tipo que se guarda es el que salga de mirar los bytes.
     * @throws InvalidImageException si el archivo esta vacio, pesa mas de {@link #MAX_EVENT_IMAGE_BYTES}, o sus
     *                               bytes no son un JPEG, PNG, GIF o WebP (SVG y demas, nunca)
     */
    Image create(byte[] data, String contentType, String fileName);

    /**
     * Lo mismo que {@link #create} pero para la portada del perfil, que admite archivos mas pesados (hasta
     * {@link #MAX_COVER_BYTES}). Mismas reglas de formato.
     */
    Image createCover(byte[] data, String contentType, String fileName);

    Optional<Image> findById(long id);

    /** Borra la imagen (por ejemplo la portada que se reemplaza). No hace nada si no existe. */
    void delete(long id);

    /**
     * Los datos del evento al que pertenece la imagen que alcanzan para decidir si quien la pide
     * puede verla: una imagen hereda la visibilidad de su evento. Vacio si no cuelga de ninguno.
     */
    Optional<EventAccessSummary> findOwnerEventAccess(long imageId);
}
