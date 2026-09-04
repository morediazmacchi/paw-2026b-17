package ar.edu.itba.paw.model;

import java.util.Date;

/**
 * Una imagen guardada en la base. Los bytes crudos del archivo viven en la columna
 * data (BYTEA); contentType es lo que se manda como header HTTP al servirla.
 */
public class Image {

    private long id;
    private byte[] data;
    private String contentType;
    private String fileName;
    private Integer fileSize;
    private String checksum;
    private Date uploadedAt;

    public Image() {
    }

    public Image(final byte[] data, final String contentType, final String fileName,
                 final Integer fileSize, final String checksum) {
        this.data = data;
        this.contentType = contentType;
        this.fileName = fileName;
        this.fileSize = fileSize;
        this.checksum = checksum;
    }

    public long getId() { return id; }
    public void setId(final long id) { this.id = id; }
    public byte[] getData() { return data; }
    public void setData(final byte[] data) { this.data = data; }
    public String getContentType() { return contentType; }
    public void setContentType(final String contentType) { this.contentType = contentType; }
    public String getFileName() { return fileName; }
    public void setFileName(final String fileName) { this.fileName = fileName; }
    public Integer getFileSize() { return fileSize; }
    public void setFileSize(final Integer fileSize) { this.fileSize = fileSize; }
    public String getChecksum() { return checksum; }
    public void setChecksum(final String checksum) { this.checksum = checksum; }
    public Date getUploadedAt() { return uploadedAt; }
    public void setUploadedAt(final Date uploadedAt) { this.uploadedAt = uploadedAt; }
}
