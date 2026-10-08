package com.agrilink.file;

import com.agrilink.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "file_assets")
public class FileAsset extends BaseEntity {

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FilePurpose purpose;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FileVisibility visibility;
    @Column(name = "original_filename", nullable = false)
    private String originalFilename;
    @Column(name = "content_type", nullable = false)
    private String contentType;
    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;
    @Column(name = "storage_key", nullable = false, unique = true)
    private String storageKey;
    @Column(name = "checksum_sha256")
    private String checksumSha256;

    protected FileAsset() {
    }

    public FileAsset(UUID ownerId, FilePurpose purpose, String originalFilename, String contentType,
                     long sizeBytes, String storageKey, String checksumSha256) {
        this.ownerId = ownerId;
        this.purpose = purpose;
        this.visibility = purpose.visibility();
        this.originalFilename = originalFilename;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.storageKey = storageKey;
        this.checksumSha256 = checksumSha256;
    }

    public UUID getOwnerId() { return ownerId; }
    public FilePurpose getPurpose() { return purpose; }
    public FileVisibility getVisibility() { return visibility; }
    public String getOriginalFilename() { return originalFilename; }
    public String getContentType() { return contentType; }
    public long getSizeBytes() { return sizeBytes; }
    public String getStorageKey() { return storageKey; }
    public String getChecksumSha256() { return checksumSha256; }
}
