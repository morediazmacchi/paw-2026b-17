package ar.edu.itba.paw.services;

import ar.edu.itba.paw.model.Image;

import java.util.Optional;

public interface ImageService {

    /**
     * Guarda una imagen nueva. Recibe los bytes crudos y no un MultipartFile a proposito:
     * MultipartFile es de la capa web y no debe filtrarse a la capa de servicios.
     *
     * @return la imagen guardada, ya con su id asignado por la base
     * @throws IllegalArgumentException si el archivo esta vacio, no es una imagen o pesa demasiado
     */
    Image create(byte[] data, String contentType, String fileName);

    Optional<Image> findById(long id);
}
