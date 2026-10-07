package bank;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Map;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;














public class StatementSigner {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String ALG = "Ed25519";




    private static SecretKeySpec aesKey() throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] key = md.digest((Config.get("OTP_PEPPER") + ":statement-signing:v1")
                .getBytes(StandardCharsets.UTF_8));
        return new SecretKeySpec(key, "AES");
    }


    private static byte[] seal(byte[] plain) throws Exception {
        byte[] iv = new byte[12];
        RANDOM.nextBytes(iv);
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.ENCRYPT_MODE, aesKey(), new GCMParameterSpec(128, iv));
        byte[] ct = c.doFinal(plain);
        byte[] blob = new byte[iv.length + ct.length];
        System.arraycopy(iv, 0, blob, 0, iv.length);
        System.arraycopy(ct, 0, blob, iv.length, ct.length);
        return blob;
    }


    private static byte[] unseal(byte[] blob) throws Exception {
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.DECRYPT_MODE, aesKey(), new GCMParameterSpec(128, blob, 0, 12));
        return c.doFinal(blob, 12, blob.length - 12);
    }


    private static PrivateKey decodePrivate(String blobB64) throws Exception {
        byte[] pkcs8 = unseal(Base64.getDecoder().decode(blobB64));
        return KeyFactory.getInstance(ALG).generatePrivate(new PKCS8EncodedKeySpec(pkcs8));
    }


    private static PublicKey decodePublic(String b64) throws Exception {
        return KeyFactory.getInstance(ALG)
                .generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(b64)));
    }








    public static KeyPair ensureKeyPair(long userId) throws Exception {

        Map<String, Object> row = Db.one(
                "SELECT signing_private_key, signing_public_key FROM users WHERE id = ?", userId);
        String pubB64 = row == null ? null : (String) row.get("signing_public_key");
        String privEnc = row == null ? null : (String) row.get("signing_private_key");

        if (pubB64 != null && privEnc != null) {

            return new KeyPair(decodePublic(pubB64), decodePrivate(privEnc));
        }

        KeyPair kp = KeyPairGenerator.getInstance(ALG).generateKeyPair();
        String newPubB64 = Base64.getEncoder().encodeToString(kp.getPublic().getEncoded());
        String newPrivEnc = Base64.getEncoder().encodeToString(seal(kp.getPrivate().getEncoded()));

        int written = Db.update(
                "UPDATE users SET signing_private_key = ?, signing_public_key = ? "
                        + "WHERE id = ? AND signing_public_key IS NULL",
                newPrivEnc, newPubB64, userId);

        if (written == 0) {

            row = Db.one("SELECT signing_private_key, signing_public_key FROM users WHERE id = ?", userId);
            return new KeyPair(decodePublic((String) row.get("signing_public_key")),
                    decodePrivate((String) row.get("signing_private_key")));
        }
        return kp;
    }

    public static String publicKeyB64(long userId) throws Exception {

        Map<String, Object> row = Db.one("SELECT signing_public_key FROM users WHERE id = ?", userId);
        if (row == null || row.get("signing_public_key") == null) {
            throw new IllegalStateException("No signing key for user " + userId);
        }
        return (String) row.get("signing_public_key");
    }




    public static String sign(long userId, byte[] statement) throws Exception {
        KeyPair kp = ensureKeyPair(userId);
        Signature s = Signature.getInstance(ALG);
        s.initSign(kp.getPrivate());
        s.update(statement);
        return Base64.getEncoder().encodeToString(s.sign());
    }





    public static boolean verify(long userId, byte[] statement, byte[] signature) throws Exception {
        Map<String, Object> row = Db.one("SELECT signing_public_key FROM users WHERE id = ?", userId);
        if (row == null || row.get("signing_public_key") == null) return false;
        Signature s = Signature.getInstance(ALG);
        s.initVerify(decodePublic((String) row.get("signing_public_key")));
        s.update(statement);
        return s.verify(signature);
    }
}