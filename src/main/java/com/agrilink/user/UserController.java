package com.agrilink.user;

import com.agrilink.common.ApiException;
import com.agrilink.file.FilePurpose;
import com.agrilink.file.FileService;
import com.agrilink.security.AuthContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserRepository users;
    private final UserMapper mapper;
    private final FileService files;

    public UserController(UserRepository users, UserMapper mapper, FileService files) {
        this.users = users;
        this.mapper = mapper;
        this.files = files;
    }

    public record UpdateMeRequest(
            @Size(min = 2, max = 150) String fullName,
            @Email @Size(max = 255) String email,
            @Pattern(regexp = "en|am|om", message = "Language must be en, am or om") String preferredLanguage,
            UUID profilePhotoFileId) {}

    @GetMapping("/me")
    @Transactional(readOnly = true)
    public UserResponse me() {
        return mapper.toResponse(current());
    }

    /** Partial update: only the supplied fields change. */
    @PatchMapping("/me")
    @Transactional
    public UserResponse updateMe(@Valid @RequestBody UpdateMeRequest request) {
        User user = current();
        if (request.fullName() != null) {
            user.setFullName(request.fullName().trim());
        }
        if (request.email() != null) {
            String email = request.email().trim();
            if (!email.equalsIgnoreCase(user.getEmail()) && users.existsByEmailIgnoreCase(email)) {
                throw ApiException.conflict("This email is already in use");
            }
            user.setEmail(email.isEmpty() ? null : email);
        }
        if (request.preferredLanguage() != null) {
            user.setPreferredLanguage(request.preferredLanguage());
        }
        if (request.profilePhotoFileId() != null) {
            files.requireOwned(request.profilePhotoFileId(), user.getId(), FilePurpose.PROFILE_PHOTO);
            user.setProfilePhotoFileId(request.profilePhotoFileId());
        }
        return mapper.toResponse(user);
    }

    private User current() {
        return users.findById(AuthContext.userId()).orElseThrow(() -> ApiException.notFound("User"));
    }
}
