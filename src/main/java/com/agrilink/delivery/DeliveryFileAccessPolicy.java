package com.agrilink.delivery;

import com.agrilink.file.FileAccessPolicy;
import com.agrilink.file.FileAsset;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Lets the buyer, farmer and driver of an order read the delivery photos attached to it. */
@Component
public class DeliveryFileAccessPolicy implements FileAccessPolicy {

    private final DeliveryAttachmentRepository attachments;

    public DeliveryFileAccessPolicy(DeliveryAttachmentRepository attachments) {
        this.attachments = attachments;
    }

    @Override
    public boolean canRead(FileAsset file, UUID userId) {
        return attachments.isAccessibleTo(file.getId(), userId);
    }
}
