package cn.minims.desk.modules.integration.minidocs.client;

import cn.minims.desk.common.api.ErrorCode;
import cn.minims.desk.common.exception.BizException;
import cn.minims.desk.framework.crypto.AesGcmCipher;
import cn.minims.desk.modules.integration.entity.IntegrationConfigDO;
import cn.minims.desk.modules.integration.mapper.IntegrationConfigMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

/**
 * MiniDocs 官方接口的唯一出口。
 *
 * <p>契约（见 docs/接口清单.md §10）：请求头 {@code Authorization: Bearer mdt_xxx}，
 * 令牌代表其所有者本人，响应统一信封 {@code {code,message,data}}。
 * 令牌从 integration_config 解密后只在内存里使用，不落日志、不返回前端。
 */
@Component
public class MiniDocsClient {

    private static final Logger log = LoggerFactory.getLogger(MiniDocsClient.class);
    private static final String PROVIDER = "minidocs";
    private static final String DEFAULT_HOST = "https://docs.minims.cn";

    private final IntegrationConfigMapper configMapper;
    private final AesGcmCipher cipher;
    private final ObjectMapper objectMapper;
    private final RestClient restClient = RestClient.builder()
            .requestFactory(requestFactory())
            .build();

    public MiniDocsClient(IntegrationConfigMapper configMapper,
                          AesGcmCipher cipher,
                          ObjectMapper objectMapper) {
        this.configMapper = configMapper;
        this.cipher = cipher;
        this.objectMapper = objectMapper;
    }

    /** 连接配置：token 为 null 表示尚未配置；siteUrl 用于拼「打开原文」链接 */
    public record Config(String token, String apiBase, String siteUrl, String apiHost,
                         boolean enabled, boolean configured) {
    }

    /** 只读检查配置，不抛异常，供状态卡使用 */
    public Config inspect() {
        IntegrationConfigDO config = configMapper.selectOne(
                Wrappers.lambdaQuery(IntegrationConfigDO.class)
                        .eq(IntegrationConfigDO::getProvider, PROVIDER)
                        .last("limit 1"));
        String host = config == null || !StringUtils.hasText(config.getApiHost())
                ? DEFAULT_HOST : config.getApiHost().trim();
        String apiBase = normalizeApiBase(host);
        String siteUrl = normalizeSiteUrl(host);
        String token = config == null ? null : cipher.decrypt(config.getApiKeyCipher());
        boolean enabled = config != null && config.getEnabled() != null && config.getEnabled() == 1;
        return new Config(token, apiBase, siteUrl, host, enabled,
                enabled && StringUtils.hasText(token));
    }

    /** 业务调用前必须拿到可用配置 */
    public Config require() {
        Config config = inspect();
        if (!config.enabled()) {
            throw new BizException(ErrorCode.KB_NOT_CONFIGURED, "知识库集成尚未启用，请先在设置页开启");
        }
        if (!StringUtils.hasText(config.token())) {
            throw new BizException(ErrorCode.KB_NOT_CONFIGURED);
        }
        return config;
    }

    /** 带令牌：/console/** 与身份类接口 */
    public JsonNode get(String path) {
        return execute(HttpMethod.GET, path, null, true);
    }

    public JsonNode post(String path, Object payload) {
        return execute(HttpMethod.POST, path, payload, true);
    }

    /** 匿名：/portal/** 在 MiniDocs 侧本就是白名单，不必要求用户先配令牌 */
    public JsonNode getPublic(String path) {
        return execute(HttpMethod.GET, path, null, false);
    }

    /** 逐段 URL 编码，中文组织名 / 中文库名 / 文档路径都安全 */
    public static String enc(String value) {
        if (value == null) {
            return "";
        }
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private JsonNode execute(HttpMethod method, String path, Object payload, boolean auth) {
        Config config = auth ? require() : inspect();
        URI uri = URI.create(config.apiBase() + path);
        try {
            RestClient.RequestBodySpec spec = restClient.method(method)
                    .uri(uri)
                    .accept(MediaType.APPLICATION_JSON);
            if (auth) {
                spec = spec.header(HttpHeaders.AUTHORIZATION, "Bearer " + config.token());
            }
            if (payload != null) {
                spec = spec.contentType(MediaType.APPLICATION_JSON).body(payload);
            }
            return spec.exchange((request, response) -> parse(response, path));
        } catch (BizException ex) {
            throw ex;
        } catch (Exception ex) {
            // 只记录路径与异常类型，绝不记录请求头（里面是令牌）
            log.warn("minidocs request failed, method={}, path={}, error={}",
                    method, path, ex.getClass().getSimpleName());
            throw new BizException(ErrorCode.KB_UPSTREAM_ERROR,
                    "无法连接知识库服务，请检查站点地址与网络");
        }
    }

    private JsonNode parse(org.springframework.http.client.ClientHttpResponse response,
                           String path) throws IOException {
        int status = response.getStatusCode().value();
        String raw = new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8);
        JsonNode root;
        try {
            root = objectMapper.readTree(raw);
        } catch (Exception ex) {
            root = objectMapper.createObjectNode();
        }
        String message = root.path("message").asText("");
        int bizCode = root.path("code").asInt(0);

        if (status == 401 || bizCode == 40100) {
            throw new BizException(ErrorCode.KB_TOKEN_INVALID,
                    "访问令牌无效、已过期或已被吊销，请到知识库 /account 重新签发");
        }
        if (status == 403 || bizCode == 40300) {
            if (message.contains("只读")) {
                throw new BizException(ErrorCode.KB_READ_ONLY);
            }
            throw new BizException(ErrorCode.KB_UPSTREAM_ERROR, "知识库拒绝访问：" + message);
        }
        if (status == 404 || bizCode == 40400) {
            throw new BizException(ErrorCode.NOT_FOUND, "知识库或文档不存在（也可能无权访问）");
        }
        if (status >= 400 || bizCode != 0) {
            throw new BizException(ErrorCode.KB_UPSTREAM_ERROR,
                    "知识库服务返回 " + status + "：" + (StringUtils.hasText(message) ? message : "未知错误"));
        }
        return root.path("data");
    }

    private static SimpleClientHttpRequestFactory requestFactory() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5_000);
        factory.setReadTimeout(12_000);
        return factory;
    }

    /** 用户可填站点根（自动补 /api），也可直接填接口基址 */
    private static String normalizeApiBase(String host) {
        String value = host.replaceAll("/+$", "");
        if (!value.startsWith("http://") && !value.startsWith("https://")) {
            throw new BizException(ErrorCode.PARAM_ERROR, "知识库站点地址需以 http(s):// 开头");
        }
        return value.endsWith("/api") ? value : value + "/api";
    }

    /** 页面根：接口基址剥掉 /api，双镜像形态再剥掉 /minidocs */
    private static String normalizeSiteUrl(String host) {
        String value = host.replaceAll("/+$", "");
        if (value.endsWith("/api")) {
            value = value.substring(0, value.length() - 4);
        }
        if (value.endsWith("/minidocs")) {
            value = value.substring(0, value.length() - "/minidocs".length());
        }
        return value;
    }
}
