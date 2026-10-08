package com.agrilink.dispute;

import com.agrilink.file.FileAccessPolicy;
import com.agrilink.file.FileAsset;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Parties of an order can read the evidence attached to its dispute, whoever uploaded it. */
@Component
public class DisputeFileAccessPolicy implements FileAccessPolicy {

    private final DisputeEvidenceRepository evidence;

    public DisputeFileAccessPolicy(DisputeEvidenceRepository evidence) {
        this.evidence = evidence;
    }

    @Override
    public boolean canRead(FileAsset file, UUID userId) {
        return evidence.isAccessibleTo(file.getId(), userId);
    }
}
