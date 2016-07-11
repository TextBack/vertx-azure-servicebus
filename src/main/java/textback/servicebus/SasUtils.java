package textback.servicebus;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;

/**
 *
 */
public class SasUtils {

    private static final String HMAC_SHA256 = "HmacSHA256";

    public static Sas getSASToken(String resourceUri, String keyName, String key, int days) throws InvalidKeyException {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expires = now.plusDays(days);
        String expiry = Long.toString(expires.toEpochSecond(ZoneOffset.UTC));

        String sasToken = null;
        try {
            String stringToSign = URLEncoder.encode(resourceUri, "UTF-8") + "\n" + expiry;
            String signature = getHMAC256(key, stringToSign);
            sasToken = "SharedAccessSignature sr=" + URLEncoder.encode(resourceUri, "UTF-8") + "&sig=" +
                    URLEncoder.encode(signature, StandardCharsets.UTF_8.name()) + "&se=" + expiry + "&skn=" + keyName;
        } catch (UnsupportedEncodingException ignored) {
        }
        Sas sas = new Sas(sasToken,now,expires);
        return sas;
    }


    public static String getHMAC256(String key, String input) throws InvalidKeyException {
        Mac sha256_HMAC;
        String hash = null;
        try {
            sha256_HMAC = Mac.getInstance(HMAC_SHA256);
            SecretKeySpec secret_key = new SecretKeySpec(key.getBytes(), HMAC_SHA256);
            sha256_HMAC.init(secret_key);
            Base64.Encoder encoder = Base64.getEncoder();
            hash = new String(encoder.encode(sha256_HMAC.doFinal(input.getBytes(StandardCharsets.UTF_8.name()))));
        } catch (InvalidKeyException e) {
            throw e;
        } catch (NoSuchAlgorithmException | UnsupportedEncodingException ignored) {
        }

        return hash;
    }
}
