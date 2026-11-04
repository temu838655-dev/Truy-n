package com.bokaliai.terminal;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Local encrypted storage for a user's Home Assistant access token. */
public final class HomeAssistantTokenStore {
    private static final String PREFS = "bo_home_assistant_tokens_v1";
    private static final String ALIAS = "bo_home_assistant_aes_v1";
    private static final int MAX_BYTES = 8192;

    private HomeAssistantTokenStore() { }

    public static boolean has(Context context, String profileId) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).contains(profileId);
    }

    public static void save(Context context, String profileId, String token) throws Exception {
        if (profileId == null || profileId.trim().isEmpty() || token == null)
            throw new IllegalArgumentException("Thiếu profile hoặc token.");
        byte[] plain = token.trim().getBytes(StandardCharsets.UTF_8);
        if (plain.length < 20 || plain.length > MAX_BYTES)
            throw new IllegalArgumentException("Token phải dài từ 20 đến 8192 byte.");
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, getKey());
        byte[] iv = cipher.getIV();
        byte[] encrypted = cipher.doFinal(plain);
        ByteBuffer packed = ByteBuffer.allocate(4 + iv.length + encrypted.length);
        packed.putInt(iv.length).put(iv).put(encrypted);
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (!prefs.edit().putString(profileId,
                Base64.encodeToString(packed.array(), Base64.NO_WRAP)).commit())
            throw new IllegalStateException("Không lưu được token đã mã hóa.");
    }

    public static String load(Context context, String profileId) throws Exception {
        String encoded = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(profileId, null);
        if (encoded == null) return null;
        ByteBuffer packed = ByteBuffer.wrap(Base64.decode(encoded, Base64.NO_WRAP));
        if (packed.remaining() < 4) throw new IllegalStateException("Dữ liệu token bị hỏng.");
        int ivLength = packed.getInt();
        if (ivLength < 12 || ivLength > 16 || packed.remaining() <= ivLength)
            throw new IllegalStateException("Dữ liệu token bị hỏng.");
        byte[] iv = new byte[ivLength];
        packed.get(iv);
        byte[] encrypted = new byte[packed.remaining()];
        packed.get(encrypted);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, getKey(), new GCMParameterSpec(128, iv));
        byte[] plain = cipher.doFinal(encrypted);
        if (plain.length > MAX_BYTES) throw new IllegalStateException("Token vượt giới hạn.");
        return new String(plain, StandardCharsets.UTF_8);
    }

    public static void delete(Context context, String profileId) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(profileId).apply();
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
