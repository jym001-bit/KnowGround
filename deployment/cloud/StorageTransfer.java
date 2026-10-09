import io.minio.BucketExistsArgs;
import io.minio.ComposeObjectArgs;
import io.minio.ComposeSource;
import io.minio.GetObjectArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.ListObjectsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.Result;
import io.minio.StatObjectArgs;
import io.minio.http.Method;
import io.minio.messages.Item;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/** Copies S3 objects through the project MinIO SDK without modifying the source bucket. */
public final class StorageTransfer {
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    public static void main(String[] args) throws Exception {
        if (args.length != 2 || !(args[0].equals("export") || args[0].equals("import") || args[0].equals("smoke") || args[0].equals("presign"))) {
            throw new IllegalArgumentException("Usage: StorageTransfer export|import|smoke|presign <backup-dir-or-ignored>");
        }
        String endpoint = required("STORAGE_ENDPOINT");
        String bucket = required("STORAGE_BUCKET");
        MinioClient client = MinioClient.builder()
                .endpoint(endpoint)
                .credentials(required("STORAGE_ACCESS_KEY"), required("STORAGE_SECRET_KEY"))
                .build();
        Path backup = Path.of(args[1]).toAbsolutePath();
        if (args[0].equals("export")) {
            exportObjects(client, bucket, backup);
        } else if (args[0].equals("import")) {
            importObjects(client, bucket, backup);
        } else if (args[0].equals("presign")) {
            presign(client, bucket, "smoke/part-2", "knowground-2");
        } else {
            smoke(client, bucket);
        }
        // MinIO's HTTP worker threads keep a short-lived CLI process alive otherwise.
        System.exit(0);
    }

    private static void smoke(MinioClient client, String bucket) throws Exception {
        if (!client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
            client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
        }
        for (int i = 1; i <= 2; i++) {
            byte[] payload = i == 1 ? new byte[5 * 1024 * 1024] : "knowground-2".getBytes(java.nio.charset.StandardCharsets.UTF_8);
            if (i == 1) java.util.Arrays.fill(payload, (byte) 'A');
            client.putObject(PutObjectArgs.builder().bucket(bucket).object("smoke/part-" + i)
                    .stream(new java.io.ByteArrayInputStream(payload), payload.length, -1)
                    .contentType("text/plain").build());
        }
        client.composeObject(ComposeObjectArgs.builder().bucket(bucket).object("smoke/combined")
                .sources(List.of(
                        ComposeSource.builder().bucket(bucket).object("smoke/part-1").build(),
                        ComposeSource.builder().bucket(bucket).object("smoke/part-2").build()))
                .build());
        var combined = client.statObject(StatObjectArgs.builder().bucket(bucket).object("smoke/combined").build());
        if (combined.size() != 5 * 1024 * 1024 + 12) throw new IllegalStateException("S3 compose size mismatch");
        presign(client, bucket, "smoke/part-2", "knowground-2");
        System.out.println("S3 bucket, put, compose and presigned GET: ok");
    }

    private static void presign(MinioClient client, String bucket, String name, String expected) throws Exception {
        String url = client.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                .method(Method.GET).bucket(bucket).object(name).expiry(60).build());
        byte[] result;
        try (InputStream in = URI.create(url).toURL().openStream()) {
            result = in.readAllBytes();
        }
        if (!new String(result, java.nio.charset.StandardCharsets.UTF_8).equals(expected)) {
            throw new IllegalStateException("S3 presigned GET returned unexpected content");
        }
        System.out.println("S3 presigned GET: ok");
    }

    private static String required(String key) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Missing " + key);
        return value;
    }

    private static void exportObjects(MinioClient client, String bucket, Path backup) throws Exception {
        if (Files.exists(backup)) throw new IllegalArgumentException("Backup directory already exists: " + backup);
        Files.createDirectories(backup.resolve("objects"));
        List<String> manifest = new ArrayList<>();
        long bytes = 0;
        for (Result<Item> result : client.listObjects(ListObjectsArgs.builder().bucket(bucket).recursive(true).build())) {
            Item item = result.get();
            String name = item.objectName();
            String encoded = ENCODER.encodeToString(name.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            Path file = backup.resolve("objects").resolve(encoded);
            var metadata = client.statObject(StatObjectArgs.builder().bucket(bucket).object(name).build());
            try (InputStream in = client.getObject(GetObjectArgs.builder().bucket(bucket).object(name).build());
                 OutputStream out = Files.newOutputStream(file)) {
                in.transferTo(out);
            }
            long size = Files.size(file);
            bytes += size;
            String contentType = metadata.contentType() == null ? "application/octet-stream" : metadata.contentType();
            manifest.add(encoded + "\t" + size + "\t" + sha256(file) + "\t" +
                    ENCODER.encodeToString(contentType.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        }
        Files.write(backup.resolve("manifest.tsv"), manifest, java.nio.charset.StandardCharsets.UTF_8);
        System.out.println("Exported " + manifest.size() + " objects, " + bytes + " bytes to " + backup);
    }

    private static void importObjects(MinioClient client, String bucket, Path backup) throws Exception {
        List<String> manifest = Files.readAllLines(backup.resolve("manifest.tsv"));
        if (!client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
            client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
        }
        long bytes = 0;
        for (String line : manifest) {
            String[] fields = line.split("\t", -1);
            if (fields.length != 4) throw new IllegalArgumentException("Invalid manifest entry");
            Path file = backup.resolve("objects").resolve(fields[0]);
            long size = Long.parseLong(fields[1]);
            if (Files.size(file) != size || !sha256(file).equals(fields[2])) {
                throw new IllegalStateException("Backup checksum mismatch: " + fields[0]);
            }
            String name = new String(DECODER.decode(fields[0]), java.nio.charset.StandardCharsets.UTF_8);
            String contentType = new String(DECODER.decode(fields[3]), java.nio.charset.StandardCharsets.UTF_8);
            try (InputStream in = Files.newInputStream(file)) {
                client.putObject(PutObjectArgs.builder().bucket(bucket).object(name)
                        .stream(in, size, -1).contentType(contentType).build());
            }
            var copied = client.statObject(StatObjectArgs.builder().bucket(bucket).object(name).build());
            if (copied.size() != size) throw new IllegalStateException("Imported object size mismatch: " + name);
            bytes += size;
        }
        System.out.println("Imported and size-verified " + manifest.size() + " objects, " + bytes + " bytes");
    }

    private static String sha256(Path path) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream in = Files.newInputStream(path)) {
            byte[] buffer = new byte[8192];
            for (int n; (n = in.read(buffer)) != -1; ) digest.update(buffer, 0, n);
        }
        return java.util.HexFormat.of().formatHex(digest.digest());
    }
}
