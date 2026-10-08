package com.agrilink.dispute;

public final class DisputeEnums {

    private DisputeEnums() {
    }

    public enum DisputeType {
        LESS_THAN_ORDERED, POOR_QUALITY, DAMAGED, WRONG_PRODUCT, NOT_DELIVERED, PAYMENT_ISSUE, OTHER
    }

    public enum DisputeStatus {
        OPEN, UNDER_REVIEW, RESOLVED;

        public boolean isOpen() {
            return this != RESOLVED;
        }
    }

    /**
     * RELEASE_ALL pays farmer and driver as normal (claim rejected); FULL_REFUND returns everything to the buyer;
     * PARTIAL uses explicit amounts, with any unallocated remainder kept as AgriLink fee.
     */
    public enum ResolutionType {
        RELEASE_ALL, FULL_REFUND, PARTIAL
    }

    public enum EvidenceKind {
        PHOTO, NOTE, SCALE_READING, CRATE_COUNT, OTHER
    }
}
