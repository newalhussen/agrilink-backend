package com.agrilink.verification;

import com.agrilink.config.AgriLinkProperties;
import com.agrilink.file.FileController;
import com.agrilink.security.AuthContext;
import com.agrilink.user.User;
import com.agrilink.user.UserRepository;
import com.agrilink.user.VerificationStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Self-service side of verification (the onboarding "Almost verified" screen). */
@RestController
@RequestMapping("/api/v1/verification")
@PreAuthorize("hasAnyRole('FARMER','BUYER','DRIVER')")
public class VerificationController {

    private final VerificationService verification;
    private final UserRepository users;
    private final String filesBasePath;

    public VerificationController(VerificationService verification, UserRepository users,
                                  AgriLinkProperties properties) {
        this.verification = verification;
        this.users = users;
        this.filesBasePath = properties.storage().publicBasePath();
    }

    public record AddDocumentRequest(@NotNull DocumentType type, @NotNull UUID fileId, @Size(max = 500) String note) {}

    public record DocumentResponse(UUID id, DocumentType type, DocumentStatus status, String fileUrl, String note,
                                   Instant uploadedAt, Instant reviewedAt) {
        public static DocumentResponse from(VerificationDocument d, String filesBasePath) {
            return new DocumentResponse(d.getId(), d.getDocType(), d.getStatus(),
                    FileController.urlFor(filesBasePath, d.getFileId()), d.getNote(), d.getCreatedAt(),
                    d.getReviewedAt());
        }
    }

    public record VerificationStatusResponse(VerificationStatus status, Set<DocumentType> requiredDocuments,
                                             List<DocumentResponse> documents, String latestNote) {}

    @GetMapping
    @Transactional(readOnly = true)
    public VerificationStatusResponse status() {
        UUID userId = AuthContext.userId();
        User user = users.findById(userId).orElseThrow();
        List<DocumentResponse> docs = verification.listDocuments(userId).stream()
                .map(d -> DocumentResponse.from(d, filesBasePath)).toList();
        String note = verification.history(userId).stream().findFirst().map(VerificationReview::getNote).orElse(null);
        return new VerificationStatusResponse(user.getVerificationStatus(), verification.requiredDocuments(user),
                docs, note);
    }

    @PostMapping("/documents")
    @ResponseStatus(HttpStatus.CREATED)
    public DocumentResponse addDocument(@Valid @RequestBody AddDocumentRequest request) {
        VerificationDocument doc = verification.addDocument(AuthContext.userId(), request.type(), request.fileId(),
                request.note());
        return DocumentResponse.from(doc, filesBasePath);
    }

    @DeleteMapping("/documents/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteDocument(@PathVariable UUID id) {
        verification.deleteDocument(AuthContext.userId(), id);
    }

    /** Asks the operations team to review the submitted documents. */
    @PostMapping("/submit")
    public VerificationStatusResponse submit() {
        verification.submit(AuthContext.userId());
        return status();
    }
}
