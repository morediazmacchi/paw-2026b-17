package ar.edu.itba.paw.model;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;
import java.util.Date;

/**
 * Una imagen guardada en la base. Los bytes crudos del archivo viven en la columna
 * data (BYTEA); contentType es lo que se manda como header HTTP al servirla.
 */
@Entity
@Table(name = "image")
public class Image {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Sin @Lob: en Postgres Hibernate lo mapearia a un large object (OID) en vez de a
    // BYTEA. Un byte[] a secas se bindea como bytea.
    @Column(name = "data", nullable = false)
    private byte[] data;

    @Column(name = "content_type", length = 100, nullable = false)
    private String contentType;

    @Column(name = "file_name", length = 255)
    private String fileName;

    @Column(name = "file_size")
    private Integer fileSize;

    // La llena el DEFAULT now() de la base: Hibernate no la inserta ni la actualiza.
    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "uploaded_at", insertable = false, updatable = false)
    private Date uploadedAt;

    public Image() {
        // Solo para Hibernate
    }

    public Image(final byte[] data, final String contentType, final String fileName,
                 final Integer fileSize) {
        this.data = data;
        this.contentType = contentType;
        this.fileName = fileName;
        this.fileSize = fileSize;
    }

    public Long getId() { return id; }
    public void setId(final Long id) { this.id = id; }
    public byte[] getData() { return data; }
    public void setData(final byte[] data) { this.data = data; }
    public String getContentType() { return contentType; }
    public void setContentType(final String contentType) { this.contentType = contentType; }
    public String getFileName() { return fileName; }
    public void setFileName(final String fileName) { this.fileName = fileName; }
    public Integer getFileSize() { return fileSize; }
    public void setFileSize(final Integer fileSize) { this.fileSize = fileSize; }
    public Date getUploadedAt() { return uploadedAt; }
    public void setUploadedAt(final Date uploadedAt) { this.uploadedAt = uploadedAt; }
}
