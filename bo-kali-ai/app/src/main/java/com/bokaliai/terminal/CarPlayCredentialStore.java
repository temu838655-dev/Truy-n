package com.bokaliai.terminal;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.nio.ByteBuffer;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Encrypted storage for user-provisioned, authorized CarPlay credentials. */
public final class CarPlayCredentialStore {
    private static final String PREFS = "bo_carplay_auth_v1";
    private static final String ALIAS = "bo_carplay_auth_aes_v1";
    private CarPlayCredentialStore() {}

    public static boolean has(Context context, String name) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).contains(name);
    }

    public static void save(Context context, String name, byte[] value) throws Exception {
        if (value == null || value.length == 0 || value.length > 256 * 1024)
            throw new IllegalArgumentException("Tệp rỗng hoặc lớn hơn 256 KB.");
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, getKey());
        byte[] iv = cipher.getIV();
        byte[] encrypted = cipher.doFinal(value);
        ByteBuffer packed = ByteBuffer.allocate(4 + iv.length + encrypted.length);
        packed.putInt(iv.length).put(iv).put(encrypted);
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (!prefs.edit().putString(name, Base64.encodeToString(packed.array(), Base64.NO_WRAP)).commit())
            throw new IllegalStateException("Không lưu được dữ liệu mã hóa.");
    }

    /** Receiver calls this only after detecting a supported CarPlay transport session. */
    public static byte[] load(Context context, String name) throws Exception {
        String encoded = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(name, null);
        if (encoded == null) return null;
        ByteBuffer packed = ByteBuffer.wrap(Base64.decode(encoded, Base64.NO_WRAP));
        int ivSize = packed.getInt();
        if (ivSize < 12 || ivSize > 16 || packed.remaining() <= ivSize)
            throw new IllegalStateException("Dữ liệu xác thực bị hỏng.");
        byte[] iv = new byte[ivSize];
        packed.get(iv);
        byte[] encrypted = new byte[packed.remaining()];
        packed.get(encrypted);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, getKey(), new GCMParameterSpec(128, iv));
        return cipher.doFinal(encrypted);
    }

    public static void clear(Context context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply();
        try {
            KeyStore store = KeyStore.getInstance("AndroidKeyStore");
            store.load(null);
            store.deleteEntry(ALIAS);
        } catch (Exception ignored) { }
    }

    private static SecretKey getKey() throws Exception {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore");
        store.load(null);
        if (store.containsAlias(ALIAS)) return (SecretKey) store.getKey(ALIAS, null);
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true).build());
        return generator.generateKey();
    }
}
