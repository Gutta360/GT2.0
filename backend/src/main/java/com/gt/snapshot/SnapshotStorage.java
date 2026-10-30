package com.gt.snapshot;

import com.gt.config.Gt2Properties;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;

@Component
public class SnapshotStorage {
    private static final byte[] MAGIC = {'G', 'T', '2', 'S', '1'};
    private final Gt2Properties properties;
    private final SecureRandom random = new SecureRandom();
    private SecretKey key;
    private Path root;

    public SnapshotStorage(Gt2Properties properties) { this.properties = properties; }

    @PostConstruct
    void initialize() {
        byte[] decoded;
        try { decoded = Base64.getDecoder().decode(properties.snapshotMasterKey()); }
        catch (IllegalArgumentException error) { throw new IllegalStateException("GT2_SNAPSHOT_MASTER_KEY must be Base64", error); }
        if (decoded.length != 32) throw new IllegalStateException("GT2_SNAPSHOT_MASTER_KEY must decode to exactly 32 bytes");
        key = new SecretKeySpec(decoded, "AES");
        root = properties.snapshotStorage().toAbsolutePath().normalize();
    }

    public String store(UUID tenantId, UUID snapshotId, byte[] plaintext) {
        try {
            byte[] iv = new byte[12]; random.nextBytes(iv);
            Cipher cipher = cipher(Cipher.ENCRYPT_MODE, iv, tenantId, snapshotId);
            byte[] encrypted = cipher.doFinal(plaintext);
            ByteBuffer blob = ByteBuffer.allocate(MAGIC.length + iv.length + encrypted.length);
            blob.put(MAGIC).put(iv).put(encrypted);
            String storageKey = tenantId + "/" + snapshotId + ".gt2snapshot";
            Path target = resolve(storageKey); Files.createDirectories(target.getParent());
            Path temporary = Files.createTempFile(target.getParent(), snapshotId.toString(), ".tmp");
            try {
                Files.write(temporary, blob.array());
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } finally { Files.deleteIfExists(temporary); }
            return storageKey;
        } catch (IOException | GeneralSecurityException error) {
            throw new IllegalStateException("Snapshot encryption or storage failed", error);
        }
    }

    public byte[] load(String storageKey, UUID tenantId, UUID snapshotId) {
        try {
            byte[] blob = Files.readAllBytes(resolve(storageKey));
            if (blob.length < MAGIC.length + 12 + 16) throw new IllegalStateException("Snapshot file is truncated");
            ByteBuffer buffer = ByteBuffer.wrap(blob);
            for (byte expected : MAGIC) if (buffer.get() != expected) throw new IllegalStateException("Snapshot file format is invalid");
            byte[] iv = new byte[12]; buffer.get(iv);
            byte[] encrypted = new byte[buffer.remaining()]; buffer.get(encrypted);
            return cipher(Cipher.DECRYPT_MODE, iv, tenantId, snapshotId).doFinal(encrypted);
        } catch (IOException | GeneralSecurityException error) {
            throw new IllegalStateException("Snapshot decryption or load failed", error);
        }
    }

    public void delete(String storageKey) {
        try { Files.deleteIfExists(resolve(storageKey)); }
        catch (IOException error) { throw new IllegalStateException("Snapshot cleanup failed", error); }
    }

    private Cipher cipher(int mode, byte[] iv, UUID tenantId, UUID snapshotId) throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(mode, key, new GCMParameterSpec(128, iv));
        cipher.updateAAD((tenantId + ":" + snapshotId).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return cipher;
    }

    private Path resolve(String storageKey) {
        Path resolved = root.resolve(storageKey).normalize();
        if (!resolved.startsWith(root)) throw new IllegalArgumentException("Invalid snapshot storage key");
        return resolved;
    }
}
