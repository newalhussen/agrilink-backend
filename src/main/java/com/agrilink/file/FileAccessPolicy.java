package com.agrilink.file;

import java.util.UUID;

/**
 * Extension point: other modules grant read access to private files that are attached to their
 * records (dispute evidence, delivery photos) without the file module knowing about them.
 */
public interface FileAccessPolicy {

    boolean canRead(FileAsset file, UUID userId);
}
