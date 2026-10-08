package com.agrilink.file;

import java.io.IOException;
import java.io.InputStream;
import org.springframework.core.io.Resource;

/** Binary storage abstraction. The local-disk implementation can be swapped for S3 or similar. */
public interface FileStorage {

    void store(String key, InputStream content) throws IOException;

    Resource load(String key);

    void delete(String key) throws IOException;
}
