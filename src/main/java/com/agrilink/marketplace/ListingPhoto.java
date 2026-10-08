package com.agrilink.marketplace;

import com.agrilink.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "listing_photos")
public class ListingPhoto extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "listing_id")
    private Listing listing;
    @Column(name = "file_id", nullable = false)
    private UUID fileId;
    @Column(name = "sort_order", nullable = false)
    private int sortOrder;
    @Column(name = "is_primary", nullable = false)
    private boolean cover;

    protected ListingPhoto() {
    }

    public ListingPhoto(Listing listing, UUID fileId, int sortOrder, boolean cover) {
        this.listing = listing;
        this.fileId = fileId;
        this.sortOrder = sortOrder;
        this.cover = cover;
    }

    public Listing getListing() { return listing; }
    public UUID getFileId() { return fileId; }
    public int getSortOrder() { return sortOrder; }
    public boolean isCover() { return cover; }
    public void setCover(boolean cover) { this.cover = cover; }
}
