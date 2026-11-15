package cn.minims.desk.modules.integration.weather.service;

import cn.minims.desk.modules.integration.weather.vo.WeatherVO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * 天气服务：全部使用免费、无需 API Key 的公开数据源，不再依赖和风天气。
 *
 * <p>优先级由 {@code minidesk.weather.providers} 配置（默认 {@code cma,open-meteo}）：
 * <ul>
 *   <li>{@code cma}：中国天气网实况，国内直连，按城市名自动解析城市代码；</li>
 *   <li>{@code open-meteo}：Open-Meteo（CC BY 4.0），先地理编码再取当前天气，适合海外或可访问该站点的网络；</li>
 *   <li>两者都失败：返回最后一次成功结果并标记 {@code stale}，无历史结果时返回稳定模拟数据。</li>
 * </ul>
 *
 * <p>外部接口一律短超时；同城结果缓存 30 分钟，城市代码 / 经纬度缓存 7 天，
 * 上一次成功的数据源会被提到队首，避免国内环境每次都白等不可达的境外接口。
 */
@Service
public class WeatherService {

    private static final Logger log = LoggerFactory.getLogger(WeatherService.class);
    private static final DateTimeFormatter DATETIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String[] TEXTS = {"晴", "多云", "阴", "小雨", "晴间多云"};

    private static final String SOURCE_CMA = "cma";
    private static final String SOURCE_OPEN_METEO = "open-meteo";
    private static final String SOURCE_MOCK = "mock";

    private static final String CMA_SEARCH_URL =
            "http://toy1.weather.com.cn/search?cityname={city}&callback=success_jsonpCallback";
    private static final String CMA_NOW_URL = "http://d1.weather.com.cn/sk_2d/{code}.html";
    private static final String OM_GEO_URL =
            "https://geocoding-api.open-meteo.com/v1/search?name={city}&count=1&language=zh&format=json";
    private static final String OM_NOW_URL = "https://api.open-meteo.com/v1/forecast"
            + "?latitude={lat}&longitude={lon}"
            + "&current=temperature_2m,relative_humidity_2m,weather_code,wind_speed_10m,wind_direction_10m"
            + "&timezone=auto";

    private static final String USER_AGENT =
            "Mozilla/5.0 (compatible; MiniDesk/1.0; +https://github.com/minimscn/minidesk)";
    private static final String CMA_REFERER = "http://www.weather.com.cn/";

    private final List<String> configuredProviders;
    private final ObjectMapper objectMapper;
    /** 外部接口必须设短超时：慢请求会把首页一起拖死 */
    private final RestClient restClient = RestClient.builder()
            .requestFactory(createRequestFactory())
            .defaultHeader("User-Agent", USER_AGENT)
            .build();

    /** 30 分钟内同城复用，避免每次进首页都打外部接口 */
    private final Cache<String, WeatherVO> cache = Caffeine.newBuilder()
            .maximumSize(64)
            .expireAfterWrite(Duration.ofMinutes(30))
            .build();
    /** 接口整体不可用时，保留最后一次真实结果（标记 stale），避免数字突然变成模拟值 */
    private final Cache<String, WeatherVO> lastGood = Caffeine.newBuilder()
            .maximumSize(64)
            .expireAfterWrite(Duration.ofHours(24))
            .build();
    private final Cache<String, String> cityCodes = Caffeine.newBuilder()
            .maximumSize(256)
            .expireAfterWrite(Duration.ofDays(7))
            .build();
    private final Cache<String, double[]> coordinates = Caffeine.newBuilder()
            .maximumSize(256)
            .expireAfterWrite(Duration.ofDays(7))
            .build();

    /** 上次成功的源优先，纯内存即可，重启后按配置顺序重新探测 */
    private volatile String preferredProvider;
    /** 境外接口连续不可达时冷却 30 分钟，避免每个新城市都白等一次超时 */
    private volatile long openMeteoRetryAfter;

    public WeatherService(ObjectMapper objectMapper,
                          @Value("${minidesk.weather.providers:cma,open-meteo}") String providers) {
        this.objectMapper = objectMapper;
        List<String> parsed = Arrays.stream(providers.split(","))
                .map(item -> item.trim().toLowerCase(Locale.ROOT))
                .filter(item -> SOURCE_CMA.equals(item) || SOURCE_OPEN_METEO.equals(item))
                .distinct()
                .toList();
        this.configuredProviders = parsed.isEmpty() ? List.of(SOURCE_CMA, SOURCE_OPEN_METEO) : parsed;
    }

    private static SimpleClientHttpRequestFactory createRequestFactory() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(2_500);
        factory.setReadTimeout(2_500);
        return factory;
    }

    public WeatherVO now(String city) {
        String targetCity = city == null || city.isBlank() ? "北京" : city.trim();
        WeatherVO cached = cache.getIfPresent(targetCity);
        if (cached != null) {
            return cached;
        }

        WeatherVO result = null;
        for (String provider : orderedProviders()) {
            result = SOURCE_CMA.equals(provider) ? tryCma(targetCity) : tryOpenMeteo(targetCity);
            if (result != null) {
                preferredProvider = provider;
                break;
            }
        }
        if (result == null) {
            WeatherVO previous = lastGood.getIfPresent(targetCity);
            result = previous == null ? mock(targetCity) : stale(previous);
        } else {
            lastGood.put(targetCity, result);
        }
        cache.put(targetCity, result);
        return result;
    }

    /** 设置页「测试连接」用：跳过 30 分钟缓存重新拉一次 */
    public WeatherVO refresh(String city) {
        cache.invalidateAll();
        return now(city);
    }

    private List<String> orderedProviders() {
        Set<String> ordered = new LinkedHashSet<>();
        if (preferredProvider != null && available(preferredProvider)) {
            ordered.add(preferredProvider);
        }
        for (String provider : configuredProviders) {
            if (available(provider)) {
                ordered.add(provider);
            }
        }
        // 只有一个数据源时，即使处于冷却期也要再试一次，避免彻底不请求
        if (ordered.isEmpty()) {
            ordered.add(configuredProviders.get(0));
        }
        return new ArrayList<>(ordered);
    }

    private boolean available(String provider) {
        return !SOURCE_OPEN_METEO.equals(provider) || System.currentTimeMillis() >= openMeteoRetryAfter;
    }

    /** 中国天气网：城市名 → 城市代码 → 实况。返回 null 表示本源不可用，由调用方继续降级。 */
    private WeatherVO tryCma(String city) {
        try {
            String code = resolveCityCode(city);
            if (code == null) {
                return null;
            }
            byte[] body = restClient.get()
                    .uri(CMA_NOW_URL, code)
                    .header("Referer", CMA_REFERER)
                    .retrieve()
                    .body(byte[].class);
            JsonNode data = parseJsonObject(decode(body));
            if (data == null || !data.hasNonNull("temp") || !data.path("temp").asText().matches("-?\\d+(\\.\\d+)?")) {
                return null;
            }
            String humidity = firstText(data, "sd", "SD").replace("%", "");
            return new WeatherVO(
                    data.path("cityname").asText(city),
                    (int) Math.round(data.path("temp").asDouble()),
                    data.path("weather").asText(""),
                    data.path("weathercode").asText(""),
                    parseInteger(humidity),
                    data.path("WD").asText(""),
                    data.path("WS").asText("").replace("级", ""),
                    false,
                    false,
                    LocalDateTime.now().format(DATETIME),
                    SOURCE_CMA);
        } catch (RuntimeException ex) {
            log.warn("weather provider cma unavailable, city={}, reason={}", city, ex.toString());
            return null;
        }
    }

    private String resolveCityCode(String city) {
        if (city.matches("\\d{9}")) {
            return city;
        }
        String cached = cityCodes.getIfPresent(city);
        if (cached != null) {
            return cached;
        }
        byte[] body = restClient.get()
                .uri(CMA_SEARCH_URL, city)
                .header("Referer", CMA_REFERER)
                .retrieve()
                .body(byte[].class);
        JsonNode list = parseJsonArray(decode(body));
        if (list == null || !list.isArray() || list.isEmpty()) {
            return null;
        }
        String ref = list.path(0).path("ref").asText("");
        String code = ref.split("~")[0].trim();
        if (!code.matches("\\d{9}")) {
            return null;
        }
        cityCodes.put(city, code);
        return code;
    }

    /** Open-Meteo：先地理编码，再按经纬度取当前天气。 */
    private WeatherVO tryOpenMeteo(String city) {
        try {
            double[] point = resolveCoordinates(city);
            if (point == null) {
                return null;
            }
            JsonNode body = restClient.get()
                    .uri(OM_NOW_URL, point[0], point[1])
                    .retrieve()
                    .body(JsonNode.class);
            JsonNode current = body == null ? null : body.path("current");
            if (current == null || !current.hasNonNull("temperature_2m")) {
                return null;
            }
            int code = current.path("weather_code").asInt(3);
            double windKmh = current.path("wind_speed_10m").asDouble(0);
            return new WeatherVO(
                    city,
                    (int) Math.round(current.path("temperature_2m").asDouble()),
                    wmoText(code),
                    String.valueOf(code),
                    current.path("relative_humidity_2m").asInt(0),
                    windDirection(current.path("wind_direction_10m").asInt(-1)),
                    String.valueOf(beaufort(windKmh)),
                    false,
                    false,
                    LocalDateTime.now().format(DATETIME),
                    SOURCE_OPEN_METEO);
        } catch (RuntimeException ex) {
            openMeteoRetryAfter = System.currentTimeMillis() + Duration.ofMinutes(30).toMillis();
            log.warn("weather provider open-meteo unavailable, city={}, reason={}", city, ex.toString());
            return null;
        }
    }

    private double[] resolveCoordinates(String city) {
        double[] cached = coordinates.getIfPresent(city);
        if (cached != null) {
            return cached;
        }
        JsonNode body = restClient.get()
                .uri(OM_GEO_URL, city)
                .retrieve()
                .body(JsonNode.class);
        JsonNode first = body == null ? null : body.path("results").path(0);
        if (first == null || first.isMissingNode() || !first.hasNonNull("latitude")) {
            return null;
        }
        double[] point = {first.path("latitude").asDouble(), first.path("longitude").asDouble()};
        coordinates.put(city, point);
        return point;
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
                LocalDateTime.now().format(DATETIME),
                SOURCE_MOCK);
    }

    private static WeatherVO stale(WeatherVO previous) {
        return new WeatherVO(
                previous.city(), previous.temp(), previous.text(), previous.icon(),
                previous.humidity(), previous.windDir(), previous.windScale(),
                false, true, previous.updatedAt(), previous.source());
    }

    /** 响应体是 JSONP / JS 变量，不是标准 JSON，需要截取其中的数组或对象。 */
    private JsonNode parseJsonArray(String raw) {
        if (raw == null) {
            return null;
        }
        int start = raw.indexOf('[');
        int end = raw.lastIndexOf(']');
        if (start < 0 || end <= start) {
            return null;
        }
        try {
            return objectMapper.readTree(raw.substring(start, end + 1));
        } catch (RuntimeException | java.io.IOException ex) {
            return null;
        }
    }

    private JsonNode parseJsonObject(String raw) {
        if (raw == null) {
            return null;
        }
        int start = raw.indexOf('{');
        int end = raw.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return null;
        }
        try {
            return objectMapper.readTree(raw.substring(start, end + 1));
        } catch (RuntimeException | java.io.IOException ex) {
            return null;
        }
    }

    /** 中国天气网部分响应是 GBK，按 UTF-8 严格解码失败时回退 GBK。 */
    private static String decode(byte[] body) {
        if (body == null) {
            return null;
        }
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(body))
                    .toString();
        } catch (CharacterCodingException ex) {
            return new String(body, java.nio.charset.Charset.forName("GBK"));
        }
    }

    private static String firstText(JsonNode node, String... fields) {
        for (String field : fields) {
            String value = node.path(field).asText("");
            if (!value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private static int parseInteger(String value) {
        String digits = value.replaceAll("[^0-9-]", "");
        if (digits.isBlank()) {
            return 0;
        }
        try {
            return Integer.parseInt(digits);
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    /** WMO 天气代码 → 中文描述，见 Open-Meteo 文档。 */
    private static String wmoText(int code) {
        return switch (code) {
            case 0 -> "晴";
            case 1 -> "晴间多云";
            case 2 -> "多云";
            case 3 -> "阴";
            case 45, 48 -> "雾";
            case 51, 53, 55, 56, 57 -> "毛毛雨";
            case 61, 63 -> "小雨";
            case 65 -> "大雨";
            case 66, 67 -> "冻雨";
            case 71, 73 -> "小雪";
            case 75, 77 -> "大雪";
            case 80, 81, 82 -> "阵雨";
            case 85, 86 -> "阵雪";
            case 95, 96, 99 -> "雷雨";
            default -> "多云";
        };
    }

    private static String windDirection(int degree) {
        if (degree < 0) {
            return "";
        }
        String[] names = {"北", "东北", "东", "东南", "南", "西南", "西", "西北"};
        return names[(int) Math.round(((degree % 360) / 45.0)) % 8] + "风";
    }

    /** km/h → 蒲福风级 */
    private static int beaufort(double kmh) {
        double[] limits = {1, 6, 12, 20, 29, 39, 50, 62, 75, 89, 103, 118};
        for (int i = 0; i < limits.length; i++) {
            if (kmh < limits[i]) {
                return i;
            }
        }
        return 12;
    }
}
