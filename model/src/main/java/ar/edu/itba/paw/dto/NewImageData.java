// Bytes crudos de una imagen recien subida, todavia sin guardar. Vive en este
// modulo (junto a EventDTO) por el mismo motivo: tanto webapp (la arma desde
// el MultipartFile) como services (la persiste con ImageService, ver
// EventServiceImpl.create()/update()) necesitan verla, sin que webapp dependa
// de services-contracts para esto ni al reves. A proposito no conoce
// MultipartFile (eso es de la capa web): ImageService.create() ya documenta
// que recibe bytes crudos, nunca un MultipartFile.
package ar.edu.itba.paw.dto;

public class NewImageData {

    private final byte[] data;
    private final String contentType;
    private final String fileName;

    public NewImageData(final byte[] data, final String contentType, final String fileName) {
        this.data = data;
        this.contentType = contentType;
        this.fileName = fileName;
    }

    public byte[] getData() { return data; }
    public String getContentType() { return contentType; }
    public String getFileName() { return fileName; }
}
