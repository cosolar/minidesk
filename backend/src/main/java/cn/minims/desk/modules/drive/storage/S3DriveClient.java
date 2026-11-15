package cn.minims.desk.modules.drive.storage;

import cn.minims.desk.common.api.ErrorCode;
import cn.minims.desk.common.exception.BizException;
import cn.minims.desk.modules.drive.config.DriveSettings;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.TreeMap;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * S3 兼容对象存储客户端（RustFS / MinIO / AWS S3）。
 *
 * <p>只用 JDK 自带的 HttpClient 手写 AWS SigV4，不引入 AWS SDK：单 Jar 体积不变，
 * 部署时也不需要额外依赖。支持 path-style 与 virtual-host 两种寻址。
 */
@Component
public class S3DriveClient {

    private static final Logger log = LoggerFactory.getLogger(S3DriveClient.class);
    private static final String ALGORITHM = "AWS4-HMAC-SHA256";
    private static final String SERVICE = "s3";
    private static final String EMPTY_HASH =
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";
    private static final DateTimeFormatter AMZ_TIME = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'");
    private static final DateTimeFormatter AMZ_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    public void put(DriveSettings settings, String key, Path file, String contentType) {
        require(settings);
        String hash = sha256Hex(file);
        try {
            HttpRequest request = signedRequest(settings, "PUT", key, Map.of(), hash,
                            contentType, HttpRequest.BodyPublishers.ofFile(file))
                    .build();
            HttpResponse<InputStream> response = send(request);
            closeQuietly(response.body());
        } catch (FileNotFoundException ex) {
            throw new BizException(ErrorCode.SERVER_ERROR, "待上传文件不存在", ex);
        }
    }

    public InputStream get(DriveSettings settings, String key) {
        require(settings);
        HttpRequest request = signedRequest(settings, "GET", key, Map.of(), EMPTY_HASH,
                null, HttpRequest.BodyPublishers.noBody()).build();
        return send(request).body();
    }

    public void delete(DriveSettings settings, String key) {
        require(settings);
        HttpRequest request = signedRequest(settings, "DELETE", key, Map.of(), EMPTY_HASH,
                null, HttpRequest.BodyPublishers.noBody()).build();
        HttpResponse<InputStream> response = send(request);
        closeQuietly(response.body());
    }

    /** 连通性自检：写一个探针对象、读回、再删掉，能覆盖读/写/删三种权限 */
    public void probe(DriveSettings settings) {
        String key = settings.normalizedPrefix() + "_minidesk_connection_test";
        Path temp = null;
        try {
            temp = Files.createTempFile("minidesk-s3-probe-", ".txt");
            Files.writeString(temp, "minidesk", StandardCharsets.UTF_8);
            put(settings, key, temp, "text/plain");
            try (InputStream in = get(settings, key)) {
                in.readAllBytes();
            }
        } catch (IOException ex) {
            throw new BizException(ErrorCode.SERVER_ERROR, "读取探针对象失败：" + ex.getMessage(), ex);
        } finally {
            if (temp != null) {
                try {
                    Files.deleteIfExists(temp);
                } catch (IOException ignored) {
                    // 临时文件清理失败不影响结果
                }
            }
            try {
                delete(settings, key);
            } catch (RuntimeException ex) {
                log.debug("s3 probe cleanup skipped: {}", ex.getMessage());
            }
        }
    }

    private void require(DriveSettings settings) {
        if (!settings.isS3() || !settings.ready()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "S3 存储尚未配置完整");
        }
        String endpoint = settings.endpoint().trim();
        if (!endpoint.startsWith("http://") && !endpoint.startsWith("https://")) {
            throw new BizException(ErrorCode.PARAM_ERROR, "Endpoint 需要以 http:// 或 https:// 开头");
        }
    }

    private HttpRequest.Builder signedRequest(DriveSettings settings, String method, String objectKey,
                                              Map<String, String> query, String payloadHash,
                                              String contentType,
                                              HttpRequest.BodyPublisher body) {
        URI endpoint = URI.create(settings.endpoint().trim());
        String scheme = endpoint.getScheme() == null ? "https" : endpoint.getScheme();
        int port = endpoint.getPort();
        boolean defaultPort = port == -1
                || ("https".equals(scheme) && port == 443)
                || ("http".equals(scheme) && port == 80);
        String authority = endpoint.getHost() + (defaultPort ? "" : ":" + port);
        String basePath = endpoint.getPath() == null ? "" : endpoint.getPath().replaceAll("/+$", "");

        String bucketHost = settings.pathStyle() ? authority : settings.bucket() + "." + authority;
        String canonicalUri = basePath
                + (settings.pathStyle() ? "/" + uriEncode(settings.bucket(), false) : "")
                + "/" + uriEncode(objectKey, false);
        String queryString = canonicalQuery(query);
        String requestUrl = scheme + "://" + bucketHost + canonicalUri
                + (queryString.isEmpty() ? "" : "?" + queryString);

        ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
        String amzTime = AMZ_TIME.format(now);
        String dateStamp = AMZ_DATE.format(now);
        String scope = dateStamp + "/" + settings.region() + "/" + SERVICE + "/aws4_request";

        // 只签 host / x-amz-* 头；Host 由 HttpClient 自动设置，不显式塞进请求（JDK 禁止）
        Map<String, String> headers = new TreeMap<>();
        headers.put("host", bucketHost);
        headers.put("x-amz-content-sha256", payloadHash);
        headers.put("x-amz-date", amzTime);
        if (StringUtils.hasText(settings.acl())) {
            headers.put("x-amz-acl", settings.acl().trim());
        }
        StringBuilder canonicalHeaders = new StringBuilder();
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            canonicalHeaders.append(entry.getKey()).append(':')
                    .append(entry.getValue().trim()).append('\n');
        }
        String signedHeaders = String.join(";", headers.keySet());

        String canonicalRequest = method + "\n" + canonicalUri + "\n" + queryString + "\n"
                + canonicalHeaders + "\n" + signedHeaders + "\n" + payloadHash;
        String stringToSign = ALGORITHM + "\n" + amzTime + "\n" + scope + "\n"
                + sha256Hex(canonicalRequest.getBytes(StandardCharsets.UTF_8));

        byte[] signingKey = signingKey(settings, dateStamp);
        String signature = hex(hmac(signingKey, stringToSign));
        String authorization = ALGORITHM + " Credential=" + settings.accessKey() + "/" + scope
                + ", SignedHeaders=" + signedHeaders + ", Signature=" + signature;

        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(requestUrl))
                .timeout(Duration.ofMinutes(5))
                .header("x-amz-date", amzTime)
                .header("x-amz-content-sha256", payloadHash)
                .header("Authorization", authorization)
                .method(method, body);
        if (StringUtils.hasText(settings.acl())) {
            builder.header("x-amz-acl", settings.acl().trim());
        }
        if (StringUtils.hasText(contentType)) {
            builder.header("Content-Type", contentType);
        }
        return builder;
    }

    private HttpResponse<InputStream> send(HttpRequest request) {
        try {
            HttpResponse<InputStream> response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                String detail = "";
                try (InputStream in = response.body()) {
                    byte[] bytes = in.readNBytes(1024);
                    detail = new String(bytes, StandardCharsets.UTF_8).replaceAll("\\s+", " ").trim();
                }
                throw new BizException(ErrorCode.SERVER_ERROR,
                        "对象存储返回 " + response.statusCode()
                                + (detail.isEmpty() ? "" : "：" + detail.substring(0, Math.min(180, detail.length()))));
            }
            return response;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new BizException(ErrorCode.SERVER_ERROR, "对象存储请求被中断", ex);
        } catch (IOException ex) {
            throw new BizException(ErrorCode.SERVER_ERROR, "对象存储连接失败：" + ex.getMessage(), ex);
        }
    }

    private byte[] signingKey(DriveSettings settings, String dateStamp) {
        byte[] kDate = hmac(("AWS4" + settings.secretKey()).getBytes(StandardCharsets.UTF_8), dateStamp);
        byte[] kRegion = hmac(kDate, settings.region());
        byte[] kService = hmac(kRegion, SERVICE);
        return hmac(kService, "aws4_request");
    }

    private static byte[] hmac(byte[] key, String message) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ex) {
            throw new IllegalStateException("HmacSHA256 unavailable", ex);
        }
    }

    private static String sha256Hex(byte[] data) {
        try {
            return hex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    private static String sha256Hex(Path file) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream in = Files.newInputStream(file)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) > 0) {
                    digest.update(buffer, 0, read);
                }
            }
            return hex(digest.digest());
        } catch (IOException | NoSuchAlgorithmException ex) {
            throw new BizException(ErrorCode.SERVER_ERROR, "计算文件校验值失败", ex);
        }
    }

    private static void closeQuietly(InputStream stream) {
        try {
            stream.close();
        } catch (IOException ignored) {
            // 响应体已消费完，关闭失败无需向上抛
        }
    }

    private static String canonicalQuery(Map<String, String> query) {
        if (query.isEmpty()) {
            return "";
        }
        Map<String, String> sorted = new TreeMap<>();
        query.forEach((key, value) -> sorted.put(uriEncode(key, true), uriEncode(value, true)));
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, String> entry : sorted.entrySet()) {
            if (builder.length() > 0) {
                builder.append('&');
            }
            builder.append(entry.getKey()).append('=').append(entry.getValue());
        }
        return builder.toString();
    }

    /** RFC 3986 编码，斜杠按需保留（对象键要保留，查询参数要转义） */
    private static String uriEncode(String value, boolean encodeSlash) {
        StringBuilder builder = new StringBuilder();
        for (byte raw : value.getBytes(StandardCharsets.UTF_8)) {
            int ch = raw & 0xFF;
            boolean unreserved = (ch >= 'A' && ch <= 'Z') || (ch >= 'a' && ch <= 'z')
                    || (ch >= '0' && ch <= '9') || ch == '-' || ch == '_' || ch == '.' || ch == '~';
            if (unreserved || (!encodeSlash && ch == '/')) {
                builder.append((char) ch);
            } else {
                builder.append('%').append(HEX[ch >> 4]).append(HEX[ch & 0x0F]);
            }
        }
        return builder.toString();
    }

    private static String hex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            int ch = value & 0xFF;
            builder.append(HEX[ch >> 4]).append(HEX[ch & 0x0F]);
        }
        return builder.toString();
    }
}
