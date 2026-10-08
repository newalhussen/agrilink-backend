package com.agrilink.file;

import com.agrilink.common.ApiException;
import com.agrilink.common.ErrorCode;
import com.agrilink.security.AuthContext;
import java.io.IOException;
import java.io.InputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class FileService {

    private static final Set<String> IMAGE_TYPES = Set.of("image/jpeg", "image/png", "image/webp");
    private static final Set<String> DOCUMENT_TYPES = Set.of("image/jpeg", "image/png", "image/webp",
            "application/pdf");

    private final FileAssetRepository repository;
    private final FileStorage storage;
    private final List<FileAccessPolicy> accessPolicies;

    public FileService(FileAssetRepository repository, FileStorage storage, List<FileAccessPolicy> accessPolicies) {
        this.repository = repository;
        this.storage = storage;
        this.accessPolicies = accessPolicies;
    }

    @Transactional
    public FileAsset upload(UUID ownerId, FilePurpose purpose, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ApiException(ErrorCode.FILE_REJECTED, "No file received");
        }
        String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase();
        boolean documentAllowed = purpose != FilePurpose.LISTING_PHOTO && purpose != FilePurpose.PROFILE_PHOTO;
        Set<String> allowed = documentAllowed ? DOCUMENT_TYPES : IMAGE_TYPES;
        if (!allowed.contains(contentType)) {
            throw new ApiException(ErrorCode.FILE_REJECTED, "Unsupported file type: " + contentType);
        }
        UUID id = UUID.randomUUID();
        String key = purpose.name().toLowerCase() + "/" + id + extensionFor(contentType);
        try (InputStream in = file.getInputStream()) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (DigestInputStream din = new DigestInputStream(in, digest)) {
                storage.store(key, din);
            }
            String checksum = HexFormat.of().formatHex(digest.digest());
            String name = file.getOriginalFilename() == null ? "upload" : file.getOriginalFilename();
            if (name.length() > 255) {
                name = name.substring(name.length() - 255);
            }
            return repository.save(new FileAsset(ownerId, purpose, name, contentType, file.getSize(), key, checksum));
        } catch (IOException | NoSuchAlgorithmException ex) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "Could not store the uploaded file");
        }
    }

    /** Loads a file the caller owns (or is an admin for) and that has the expected purpose. */
    @Transactional(readOnly = true)
    public FileAsset requireOwned(UUID fileId, UUID ownerId, FilePurpose purpose) {
        FileAsset file = repository.findById(fileId).orElseThrow(() -> ApiException.notFound("File"));
        if (!file.getOwnerId().equals(ownerId) && !AuthContext.isAdmin()) {
            throw ApiException.forbidden("You can only attach files that you uploaded");
        }
        if (file.getPurpose() != purpose) {
            throw ApiException.badRequest("File was uploaded for a different purpose: " + file.getPurpose());
        }
        return file;
    }

    @Transactional(readOnly = true)
    public FileAsset get(UUID fileId) {
        return repository.findById(fileId).orElseThrow(() -> ApiException.notFound("File"));
    }

    /** Public files are readable by anyone; private files by owner, admins, or policy-granted users. */
    @Transactional(readOnly = true)
    public FileAsset requireReadable(UUID fileId) {
        FileAsset file = get(fileId);
        if (file.getVisibility() == FileVisibility.PUBLIC) {
            return file;
        }
        Optional<UUID> caller = AuthContext.optionalUserId();
        if (caller.isEmpty()) {
            throw new ApiException(ErrorCode.UNAUTHENTICATED, "Authentication required");
        }
        UUID userId = caller.get();
        boolean allowed = file.getOwnerId().equals(userId) || AuthContext.isAdmin()
                || accessPolicies.stream().anyMatch(p -> p.canRead(file, userId));
        if (!allowed) {
            throw ApiException.forbidden("You do not have access to this file");
        }
        return file;
    }

    public Resource content(FileAsset file) {
        Resource resource = storage.load(file.getStorageKey());
        if (!resource.exists()) {
            throw ApiException.notFound("File content");
        }
        return resource;
    }

    private static String extensionFor(String contentType) {
        return switch (contentType) {
            case "image/jpeg" -> ".jpg";
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            case "application/pdf" -> ".pdf";
            default -> "";
        };
    }
}
