package com.agrilink.file;

public enum FilePurpose {
    LISTING_PHOTO(FileVisibility.PUBLIC),
    PROFILE_PHOTO(FileVisibility.PUBLIC),
    VERIFICATION_DOCUMENT(FileVisibility.PRIVATE),
    DISPUTE_EVIDENCE(FileVisibility.PRIVATE),
    DELIVERY_EVIDENCE(FileVisibility.PRIVATE);

    private final FileVisibility visibility;

    FilePurpose(FileVisibility visibility) {
        this.visibility = visibility;
    }

    public FileVisibility visibility() {
        return visibility;
    }
}
