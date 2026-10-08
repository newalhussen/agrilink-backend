package com.agrilink.file;

import com.agrilink.config.AgriLinkProperties;
import com.agrilink.security.AuthContext;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/files")
public class FileController {

    private final FileService files;
    private final String publicBasePath;

    public FileController(FileService files, AgriLinkProperties properties) {
        this.files = files;
        this.publicBasePath = properties.storage().publicBasePath();
    }

    public record FileResponse(UUID id, String url, String contentType, long sizeBytes, FilePurpose purpose,
                               FileVisibility visibility, String originalFilename) {}

    public static String urlFor(String basePath, UUID fileId) {
        return basePath + "/" + fileId;
    }

    /** Upload as multipart/form-data: {@code file} plus {@code purpose}. Returns an id to attach to other resources. */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public FileResponse upload(@RequestParam("file") MultipartFile file, @RequestParam("purpose") FilePurpose purpose) {
        FileAsset asset = files.upload(AuthContext.userId(), purpose, file);
        return new FileResponse(asset.getId(), urlFor(publicBasePath, asset.getId()), asset.getContentType(),
                asset.getSizeBytes(), asset.getPurpose(), asset.getVisibility(), asset.getOriginalFilename());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Resource> download(@PathVariable UUID id) {
        FileAsset asset = files.requireReadable(id);
        CacheControl cache = asset.getVisibility() == FileVisibility.PUBLIC
                ? CacheControl.maxAge(7, TimeUnit.DAYS).cachePublic()
                : CacheControl.noStore();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(asset.getContentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                .header("X-Content-Type-Options", "nosniff")
                .cacheControl(cache)
                .body(files.content(asset));
    }
}
