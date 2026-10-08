package com.agrilink.verification;

import com.agrilink.admin.AdminAuditService;
import com.agrilink.buyer.BuyerProfile;
import com.agrilink.buyer.BuyerProfileRepository;
import com.agrilink.common.ApiException;
import com.agrilink.common.ErrorCode;
import com.agrilink.driver.DriverProfile;
import com.agrilink.driver.DriverProfileRepository;
import com.agrilink.farmer.FarmerProfile;
import com.agrilink.farmer.FarmerProfileRepository;
import com.agrilink.file.FilePurpose;
import com.agrilink.file.FileService;
import com.agrilink.user.Role;
import com.agrilink.user.User;
import com.agrilink.user.UserRepository;
import com.agrilink.user.VerificationStatus;
import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Document upload, submission for review, and admin decisions. */
@Service
public class VerificationService {

    private final UserRepository users;
    private final VerificationDocumentRepository documents;
    private final VerificationReviewRepository reviews;
    private final FarmerProfileRepository farmerProfiles;
    private final BuyerProfileRepository buyerProfiles;
    private final DriverProfileRepository driverProfiles;
    private final FileService files;
    private final AdminAuditService audit;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public VerificationService(UserRepository users, VerificationDocumentRepository documents,
                               VerificationReviewRepository reviews, FarmerProfileRepository farmerProfiles,
                               BuyerProfileRepository buyerProfiles, DriverProfileRepository driverProfiles,
                               FileService files, AdminAuditService audit, ApplicationEventPublisher events,
                               Clock clock) {
        this.users = users;
        this.documents = documents;
        this.reviews = reviews;
        this.farmerProfiles = farmerProfiles;
        this.buyerProfiles = buyerProfiles;
        this.driverProfiles = driverProfiles;
        this.files = files;
        this.audit = audit;
        this.events = events;
        this.clock = clock;
    }

    @Transactional
    public VerificationDocument addDocument(UUID userId, DocumentType type, UUID fileId, String note) {
        User user = requireUser(userId);
        if (user.getRole() == Role.ADMIN) {
            throw ApiException.badRequest("Admins do not need verification");
        }
        files.requireOwned(fileId, userId, FilePurpose.VERIFICATION_DOCUMENT);
        return documents.save(new VerificationDocument(userId, type, fileId, note));
    }

    @Transactional(readOnly = true)
    public List<VerificationDocument> listDocuments(UUID userId) {
        return documents.findByUserIdOrderByCreatedAtDesc(userId);
    }

    @Transactional
    public void deleteDocument(UUID userId, UUID documentId) {
        VerificationDocument doc = documents.findById(documentId)
                .filter(d -> d.getUserId().equals(userId))
                .orElseThrow(() -> ApiException.notFound("Document"));
        if (doc.getStatus() == DocumentStatus.APPROVED) {
            throw ApiException.conflict("Approved documents cannot be removed");
        }
        documents.delete(doc);
    }

    /** Document types that must be on file before a user may ask for review. */
    public Set<DocumentType> requiredDocuments(User user) {
        return switch (user.getRole()) {
            case FARMER -> EnumSet.of(DocumentType.FAYDA_ID);
            case DRIVER -> EnumSet.of(DocumentType.DRIVING_LICENSE, DocumentType.VEHICLE_REGISTRATION);
            case BUYER -> buyerProfiles.findByUserId(user.getId()).map(BuyerProfile::getBuyerType)
                    .filter(t -> t.isBusiness()).isPresent()
                    ? EnumSet.of(DocumentType.BUSINESS_LICENSE) : EnumSet.of(DocumentType.FAYDA_ID);
            case ADMIN -> EnumSet.noneOf(DocumentType.class);
        };
    }

    @Transactional
    public VerificationStatus submit(UUID userId) {
        User user = requireUser(userId);
        if (user.getRole() == Role.ADMIN) {
            throw ApiException.badRequest("Admins do not need verification");
        }
        if (!user.isPhoneVerified()) {
            throw new ApiException(ErrorCode.PHONE_NOT_VERIFIED, "Verify your phone number first");
        }
        VerificationStatus status = user.getVerificationStatus();
        if (status == VerificationStatus.VERIFIED || status == VerificationStatus.PENDING) {
            throw ApiException.conflict("Verification is already " + status.name().toLowerCase());
        }
        Set<DocumentType> uploaded = documents.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(VerificationDocument::getDocType).collect(Collectors.toSet());
        Set<DocumentType> missing = EnumSet.noneOf(DocumentType.class);
        missing.addAll(requiredDocuments(user));
        missing.removeAll(uploaded);
        if (!missing.isEmpty()) {
            throw ApiException.badRequest("Missing required documents: " + missing);
        }
        assertProfileComplete(user);
        user.setVerificationStatus(VerificationStatus.PENDING);
        return user.getVerificationStatus();
    }

    @Transactional
    public User decide(UUID adminId, UUID userId, VerificationDecision decision, String note) {
        User user = requireUser(userId);
        if (user.getRole() == Role.ADMIN) {
            throw ApiException.badRequest("Admins cannot be verified or rejected");
        }
        if (decision != VerificationDecision.APPROVE && (note == null || note.isBlank())) {
            throw ApiException.badRequest("A note is required when rejecting or asking for more information");
        }
        VerificationStatus previous = user.getVerificationStatus();
        VerificationStatus next = switch (decision) {
            case APPROVE -> VerificationStatus.VERIFIED;
            case REJECT -> VerificationStatus.REJECTED;
            case REQUEST_INFO -> VerificationStatus.INFO_NEEDED;
        };
        user.setVerificationStatus(next);
        Instant now = Instant.now(clock);
        if (decision == VerificationDecision.APPROVE) {
            documents.findByUserIdOrderByCreatedAtDesc(userId).stream()
                    .filter(d -> d.getStatus() == DocumentStatus.PENDING)
                    .forEach(d -> d.review(DocumentStatus.APPROVED, adminId, now));
        }
        reviews.save(new VerificationReview(userId, adminId, decision, previous, next, note));
        audit.record(adminId, "VERIFICATION_" + decision.name(), "USER", userId, note);
        events.publishEvent(new VerificationDecidedEvent(userId, decision, note));
        return user;
    }

    @Transactional(readOnly = true)
    public List<VerificationReview> history(UUID userId) {
        return reviews.findByUserIdOrderByCreatedAtDesc(userId);
    }

    private void assertProfileComplete(User user) {
        switch (user.getRole()) {
            case FARMER -> {
                FarmerProfile p = farmerProfiles.findByUserId(user.getId())
                        .orElseThrow(() -> ApiException.notFound("Farmer profile"));
                if (p.getAddress().getRegionId() == null) {
                    throw ApiException.badRequest("Add your region and location before requesting verification");
                }
            }
            case DRIVER -> {
                DriverProfile p = driverProfiles.findByUserId(user.getId())
                        .orElseThrow(() -> ApiException.notFound("Driver profile"));
                if (!p.hasVehicle() || p.getLicenseNumber() == null) {
                    throw ApiException.badRequest("Add your licence number and vehicle details first");
                }
            }
            default -> { }
        }
    }

    private User requireUser(UUID userId) {
        return users.findById(userId).orElseThrow(() -> ApiException.notFound("User"));
    }
}
