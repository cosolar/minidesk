package cn.minims.desk.modules.integration.weather.service;

import cn.minims.desk.framework.crypto.AesGcmCipher;
import cn.minims.desk.modules.integration.entity.IntegrationConfigDO;
import cn.minims.desk.modules.integration.mapper.IntegrationConfigMapper;
import cn.minims.desk.modules.integration.weather.vo.WeatherVO;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.JsonNode;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * 天气服务。
 *
 * <p>未配置和风天气 Key 时返回稳定的模拟数据（同一城市结果恒定，便于调试），
 * 并标记 isMock = true，前端显示「模拟」角标。
 * 配置了 Key 但请求失败时回退到模拟数据，不影响首页其余部分。
 */
@Service
public class WeatherService {

    private static final Logger log = LoggerFactory.getLogger(WeatherService.class);
    private static final DateTimeFormatter DATETIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String[] TEXTS = {"晴", "多云", "阴", "小雨", "晴间多云"};

    private final IntegrationConfigMapper configMapper;
    private final AesGcmCipher cipher;
    /** 外部接口必须设短超时：慢请求会把首页一起拖死 */
    private final RestClient restClient = RestClient.builder()
            .requestFactory(createRequestFactory())
            .build();

    /** 30 分钟内同城复用，避免每次进首页都打外部接口 */
    private final Cache<String, WeatherVO> cache = Caffeine.newBuilder()
            .maximumSize(64)
            .expireAfterWrite(Duration.ofMinutes(30))
            .build();

    public WeatherService(IntegrationConfigMapper configMapper, AesGcmCipher cipher) {
        this.configMapper = configMapper;
        this.cipher = cipher;
    }

    private static SimpleClientHttpRequestFactory createRequestFactory() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3_000);
        factory.setReadTimeout(3_000);
        return factory;
    }

    public WeatherVO now(String city) {
        String targetCity = city == null || city.isBlank() ? "北京" : city.trim();
        WeatherVO cached = cache.getIfPresent(targetCity);
        if (cached != null) {
            return cached;
        }
        WeatherVO result = tryReal(targetCity);
        if (result == null) {
            result = mock(targetCity);
        }
        cache.put(targetCity, result);
        return result;
    }

    /** 设置页「测试连接」用：跳过缓存重新拉一次 */
    public WeatherVO refresh(String city) {
        cache.invalidateAll();
        return now(city);
    }

    private WeatherVO tryReal(String city) {
        IntegrationConfigDO config;
        try {
            config = configMapper.selectOne(Wrappers.lambdaQuery(IntegrationConfigDO.class)
                    .eq(IntegrationConfigDO::getProvider, "qweather")
                    .last("limit 1"));
        } catch (RuntimeException ex) {
            return null;
        }
        if (config == null || config.getEnabled() == null || config.getEnabled() != 1) {
            return null;
        }
        String key = cipher.decrypt(config.getApiKeyCipher());
        if (key == null || key.isBlank()) {
            return null;
        }
        String host = config.getApiHost() == null || config.getApiHost().isBlank()
                ? "https://devapi.qweather.com" : config.getApiHost().trim();

        try {
            String geoHost = host.replace("devapi", "geoapi");
            JsonNode geo = restClient.get()
                    .uri(geoHost + "/v7/city/lookup?location={city}&key={key}", city, key)
                    .retrieve()
                    .body(JsonNode.class);
            String locationId = geo == null ? null : geo.path("location").path(0).path("id").asText(null);
            if (locationId == null) {
                return null;
            }

            JsonNode body = restClient.get()
                    .uri(host + "/v7/weather/now?location={id}&key={key}", locationId, key)
                    .retrieve()
                    .body(JsonNode.class);
            if (body == null || !"200".equals(body.path("code").asText())) {
                return null;
            }
            JsonNode now = body.path("now");
            return new WeatherVO(
                    city,
                    now.path("temp").asInt(),
                    now.path("text").asText(""),
                    now.path("icon").asText(""),
                    now.path("humidity").asInt(),
                    now.path("windDir").asText(""),
                    now.path("windScale").asText(""),
                    false,
                    false,
                    LocalDateTime.now().format(DATETIME));
        } catch (RuntimeException ex) {
            log.warn("qweather request failed, fallback to mock, city={}", city, ex);
            return null;
        }
    }

    /** 稳定模拟：同一城市每次返回一致，避免数字乱跳 */
    private WeatherVO mock(String city) {
        int hash = Math.abs(city.hashCode());
        int temp = 15 + hash % 14;
        String text = TEXTS[hash % TEXTS.length];
        return new WeatherVO(
                city, temp, text, "100",
                40 + hash % 45,
                "东南风", String.valueOf(1 + hash % 4),
                true, false,
                LocalDateTime.now().format(DATETIME));
    }
}
