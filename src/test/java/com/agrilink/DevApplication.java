package com.agrilink;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.io.IOException;
import java.nio.file.Path;
import org.springframework.boot.SpringApplication;

/**
 * Runs the whole backend on a throw-away embedded PostgreSQL with the dev profile: no installed database, no
 * Docker, no password. Handy for working on the clients (admin web, Android) on a machine without PostgreSQL.
 *
 * <pre>
 *   mvn -q test-compile dependency:build-classpath -Dmdep.outputFile=target/cp.txt -Dmdep.includeScope=test
 *   java -Dnet.bytebuddy.experimental=true -cp "target/test-classes;target/classes;{cp.txt}" com.agrilink.DevApplication
 * </pre>
 *
 * Data is kept in target/dev-db between runs; delete that folder to start fresh.
 */
public final class DevApplication {

    private DevApplication() {
    }

    public static void main(String[] args) throws IOException {
        int port = Integer.getInteger("dev.db.port", 54329);
        EmbeddedPostgres postgres = EmbeddedPostgres.builder()
                .setPort(port)
                .setDataDirectory(Path.of("target", "dev-db"))
                .setCleanDataDirectory(false)
                .setServerConfig("fsync", "off")
                .setServerConfig("synchronous_commit", "off")
                .setServerConfig("full_page_writes", "off")
                .start();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                postgres.close();
            } catch (IOException ignored) {
                // shutting down anyway
            }
        }));
        System.setProperty("spring.datasource.url", postgres.getJdbcUrl("postgres", "postgres"));
        System.setProperty("spring.datasource.username", "postgres");
        System.setProperty("spring.datasource.password", "");
        System.setProperty("spring.profiles.active", "dev");
        System.out.println("AgriLink dev backend: embedded PostgreSQL on port " + port + ", API on http://localhost:8080");
        SpringApplication.run(AgriLinkApplication.class, args);
    }
}
