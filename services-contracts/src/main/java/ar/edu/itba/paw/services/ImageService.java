package ar.edu.itba.paw.services;

import ar.edu.itba.paw.model.Image;

import java.util.Optional;

public interface ImageService {

    /**
     * Guarda una imagen nueva. Recibe los bytes crudos y no un MultipartFile a proposito:
     * MultipartFile es de la capa web y no debe filtrarse a la capa de servicios.
     *
     * @return la imagen guardada, ya con su id asignado por la base
     * @param contentType lo que declaro el cliente: no se le cree. Solo se usa como primer
     *                    filtro; el tipo que se guarda es el que salga de mirar los bytes.
     * @throws IllegalArgumentException si el archivo esta vacio, pesa demasiado, o sus bytes no
     *                                  son un JPEG, PNG, GIF o WebP (SVG y demas, nunca)
     */
    Image create(byte[] data, String contentType, String fileName);

    Optional<Image> findById(long id);

    /**
     * Id del evento al que pertenece la imagen. Sirve para decidir si quien la pide
     * puede verla: una imagen hereda la visibilidad de su evento.
     */
    Optional<Long> findOwnerEventId(long imageId);
}
