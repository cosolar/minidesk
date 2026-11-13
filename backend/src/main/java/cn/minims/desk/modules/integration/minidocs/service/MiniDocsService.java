package cn.minims.desk.modules.integration.minidocs.service;

import cn.minims.desk.common.api.ErrorCode;
import cn.minims.desk.common.exception.BizException;
import cn.minims.desk.modules.integration.minidocs.client.MiniDocsClient;
import cn.minims.desk.modules.integration.minidocs.vo.MiniDocsVO;
import com.fasterxml.jackson.databind.JsonNode;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 知识库对接的业务层。
 *
 * <p>两种来源：{@code mine} 走带令牌的 /console/{org}/...（含私有库），
 * {@code public} 走匿名门户 /portal/...（只读、只看已发布内容）。
 * 目录树缓存 5 分钟，正文不缓存——避免 MiniDesk 与知识库出现两份过期内容。
 */
@Service
public class MiniDocsService {

    private static final String SCOPE_MINE = "mine";
    private static final String SCOPE_PUBLIC = "public";
    private static final int MAX_SEARCH = 50;

    private final MiniDocsClient client;
    private final Cache<String, List<MiniDocsVO.DocNode>> treeCache = Caffeine.newBuilder()
            .maximumSize(32)
            .expireAfterWrite(Duration.ofMinutes(5))
            .build();

    public MiniDocsService(MiniDocsClient client) {
        this.client = client;
    }

    /** 状态卡：未配置或连接失败都返回结构化的说明，不抛异常打断页面 */
    public MiniDocsVO.Status status() {
        MiniDocsClient.Config config = client.inspect();
        if (!config.configured()) {
            return new MiniDocsVO.Status(false, config.enabled(), false,
                    config.apiHost(), config.siteUrl(), "", "", "", List.of(),
                    config.enabled() ? "尚未填写访问令牌" : "知识库集成尚未启用");
        }
        try {
            JsonNode me = client.get("/auth/me");
            List<MiniDocsVO.Tenant> tenants = tenants();
            String org = tenants.isEmpty() ? "" : tenants.get(0).slug();
            return new MiniDocsVO.Status(true, true, true,
                    config.apiHost(), config.siteUrl(),
                    me.path("username").asText(""), me.path("displayName").asText(""),
                    org, tenants, "连接正常");
        } catch (BizException ex) {
            return new MiniDocsVO.Status(true, true, false,
                    config.apiHost(), config.siteUrl(), "", "", "", List.of(), ex.getMessage());
        }
    }

    /** 设置页「测试连接」：把失败变成明确的业务错误 */
    public MiniDocsVO.Status test() {
        MiniDocsVO.Status result = status();
        if (!result.connected()) {
            throw new BizException(ErrorCode.KB_UPSTREAM_ERROR, result.message());
        }
        return result;
    }

    public List<MiniDocsVO.Tenant> tenants() {
        JsonNode data = client.get("/tenants/mine");
        List<MiniDocsVO.Tenant> result = new ArrayList<>();
        if (data.isArray()) {
            for (JsonNode node : data) {
                result.add(new MiniDocsVO.Tenant(
                        node.path("slug").asText(""),
                        node.path("name").asText(""),
                        node.path("role").asText("")));
            }
        }
        return result;
    }

    public String defaultOrg() {
        List<MiniDocsVO.Tenant> list = tenants();
        if (list.isEmpty()) {
            throw new BizException(ErrorCode.KB_UPSTREAM_ERROR, "当前账号没有可用组织，请先在知识库创建组织");
        }
        return list.get(0).slug();
    }

    public MiniDocsVO.BookPage books(String scope, String org, String keyword, long page, long size) {
        checkScope(scope);
        long safePage = Math.max(1, page);
        long safeSize = Math.min(Math.max(1, size), 100);
        StringBuilder path = new StringBuilder();
        if (SCOPE_MINE.equals(scope)) {
            String target = StringUtils.hasText(org) ? org : defaultOrg();
            path.append("/console/").append(MiniDocsClient.enc(target)).append("/kbs");
        } else {
            path.append("/portal/kbs");
        }
        path.append("?page=").append(safePage).append("&size=").append(safeSize);
        if (StringUtils.hasText(keyword)) {
            path.append("&keyword=").append(MiniDocsClient.enc(keyword.trim()));
        }
        JsonNode data = SCOPE_MINE.equals(scope)
                ? client.get(path.toString()) : client.getPublic(path.toString());
        List<MiniDocsVO.Book> books = new ArrayList<>();
        JsonNode list = data.path("list");
        if (list.isArray()) {
            for (JsonNode node : list) {
                books.add(toBook(node, scope));
            }
        }
        return new MiniDocsVO.BookPage(books, data.path("total").asLong(books.size()), safePage, safeSize);
    }

    public List<MiniDocsVO.DocNode> tree(String scope, String org, String slug) {
        checkScope(scope);
        requireSlug(slug);
        String target = SCOPE_MINE.equals(scope) && !StringUtils.hasText(org) ? defaultOrg() : org;
        String key = scope + ":" + target + ":" + slug;
        List<MiniDocsVO.DocNode> cached = treeCache.getIfPresent(key);
        if (cached != null) {
            return cached;
        }
        JsonNode nodes;
        if (SCOPE_MINE.equals(scope)) {
            nodes = client.get("/console/" + MiniDocsClient.enc(target)
                    + "/kbs/" + MiniDocsClient.enc(slug) + "/tree");
        } else {
            nodes = client.getPublic("/portal/kb/" + MiniDocsClient.enc(target)
                    + "/" + MiniDocsClient.enc(slug)).path("view").path("tree");
        }
        List<MiniDocsVO.DocNode> result = toNodes(nodes);
        treeCache.put(key, result);
        return result;
    }

    public MiniDocsVO.DocContent doc(String scope, String org, String slug, String path) {
        checkScope(scope);
        requireSlug(slug);
        if (!StringUtils.hasText(path)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "缺少文档路径");
        }
        String target = SCOPE_MINE.equals(scope) && !StringUtils.hasText(org) ? defaultOrg() : org;
        MiniDocsClient.Config config = client.inspect();
        String sourceUrl = config.siteUrl() + "/kb/" + MiniDocsClient.enc(target)
                + "/" + MiniDocsClient.enc(slug) + "?path=" + MiniDocsClient.enc(path);

        if (SCOPE_MINE.equals(scope)) {
            JsonNode data = client.get("/console/" + MiniDocsClient.enc(target)
                    + "/kbs/" + MiniDocsClient.enc(slug) + "/doc?path=" + MiniDocsClient.enc(path));
            return new MiniDocsVO.DocContent(scope,
                    data.path("path").asText(path),
                    data.path("name").asText(""),
                    StringUtils.hasText(data.path("title").asText(""))
                            ? data.path("title").asText("") : data.path("name").asText(""),
                    data.path("content").asText(""),
                    "", data.path("modifiedAt").asText(""),
                    data.path("size").asLong(0), sourceUrl);
        }

        JsonNode view = client.getPublic("/portal/kb/" + MiniDocsClient.enc(target)
                + "/" + MiniDocsClient.enc(slug) + "?path=" + MiniDocsClient.enc(path))
                .path("view");
        String html = view.path("html").asText("");
        String assetPrefix = view.path("assetPrefix").asText("");
        String linkPrefix = view.path("docLinkPrefix").asText("");
        // 服务端渲染出来的相对地址指向知识库站点，改写成绝对地址才能在 MiniDesk 里打开
        if (StringUtils.hasText(assetPrefix)) {
            html = html.replace("\"" + assetPrefix, "\"" + config.siteUrl() + assetPrefix);
        }
        if (StringUtils.hasText(linkPrefix)) {
            html = html.replace("\"" + linkPrefix, "\"" + config.siteUrl() + linkPrefix);
        }
        return new MiniDocsVO.DocContent(scope,
                view.path("currentPath").asText(path),
                view.path("currentName").asText(""),
                view.path("title").asText(""),
                "", html, view.path("updatedText").asText(""),
                view.path("lineCount").asLong(0), sourceUrl);
    }

    /** 站内检索：基于目录树匹配文档名 / 路径，不逐篇拉正文 */
    public List<MiniDocsVO.SearchHit> search(String scope, String org, String slug,
                                             String keyword, String bookName, int limit) {
        checkScope(scope);
        requireSlug(slug);
        if (!StringUtils.hasText(keyword)) {
            return List.of();
        }
        String needle = keyword.trim().toLowerCase(Locale.ROOT);
        int max = Math.min(Math.max(1, limit), MAX_SEARCH);
        String target = SCOPE_MINE.equals(scope) && !StringUtils.hasText(org) ? defaultOrg() : org;
        List<MiniDocsVO.SearchHit> hits = new ArrayList<>();
        collect(tree(scope, target, slug), needle, bookName == null ? "" : bookName,
                target, slug, scope, hits, max);
        return hits;
    }

    /** 沉淀：把笔记写成知识库文档，需要 read,write 令牌 */
    public MiniDocsVO.CreateResult createDoc(String org, String slug, String path, String content) {
        requireSlug(slug);
        String safePath = safePath(path);
        if (!StringUtils.hasText(content)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "文档内容不能为空");
        }
        String target = StringUtils.hasText(org) ? org : defaultOrg();
        Map<String, String> body = new LinkedHashMap<>();
        body.put("path", safePath);
        body.put("content", content);
        JsonNode data = client.post("/console/" + MiniDocsClient.enc(target)
                + "/kbs/" + MiniDocsClient.enc(slug) + "/doc", body);
        treeCache.invalidateAll();
        MiniDocsClient.Config config = client.require();
        return new MiniDocsVO.CreateResult(
                data.path("path").asText(safePath),
                data.path("name").asText(""),
                config.siteUrl() + "/kb/" + MiniDocsClient.enc(target)
                        + "/" + MiniDocsClient.enc(slug) + "?path=" + MiniDocsClient.enc(safePath));
    }

    /** 空态引导：在知识库站点创建一个私有库 */
    public MiniDocsVO.Book createBook(String org, String name, String description, String visibility) {
        if (!StringUtils.hasText(name)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "知识库名称不能为空");
        }
        String target = StringUtils.hasText(org) ? org : defaultOrg();
        Map<String, String> body = new LinkedHashMap<>();
        body.put("name", name.trim());
        body.put("description", description == null ? "" : description);
        body.put("visibility", StringUtils.hasText(visibility) ? visibility : "private");
        body.put("maintainScope", "owner_only");
        JsonNode data = client.post("/console/" + MiniDocsClient.enc(target) + "/kbs", body);
        treeCache.invalidateAll();
        return toBook(data, SCOPE_MINE);
    }

    private MiniDocsVO.Book toBook(JsonNode node, String scope) {
        MiniDocsClient.Config config = client.inspect();
        String orgSlug = node.path("tenantSlug").asText("");
        List<String> tags = new ArrayList<>();
        JsonNode tagNodes = node.path("tags");
        if (tagNodes.isArray()) {
            tagNodes.forEach(tag -> tags.add(tag.asText("")));
        }
        String slug = node.path("slug").asText("");
        String cover = node.path("coverSrc").asText("");
        if (StringUtils.hasText(cover) && cover.startsWith("/")) {
            cover = config.siteUrl() + cover;
        }
        String url = config.siteUrl() + "/kb/"
                + (StringUtils.hasText(orgSlug) ? MiniDocsClient.enc(orgSlug) + "/" : "")
                + MiniDocsClient.enc(slug);
        return new MiniDocsVO.Book(
                node.path("id").asText(""),
                node.path("name").asText(""),
                slug, orgSlug,
                node.path("description").asText(""),
                node.path("visibility").asText(""),
                node.path("docCount").asInt(0),
                tags,
                node.path("updatedText").asText(""),
                cover, scope, url);
    }

    private List<MiniDocsVO.DocNode> toNodes(JsonNode array) {
        List<MiniDocsVO.DocNode> result = new ArrayList<>();
        if (!array.isArray()) {
            return result;
        }
        for (JsonNode node : array) {
            result.add(new MiniDocsVO.DocNode(
                    node.path("name").asText(""),
                    node.path("path").asText(""),
                    node.path("encodedPath").asText(""),
                    node.path("type").asText(""),
                    node.path("size").asLong(0),
                    node.path("modifiedAt").asText(""),
                    toNodes(node.path("children"))));
        }
        return result;
    }

    private void collect(List<MiniDocsVO.DocNode> nodes, String needle, String bookName,
                         String org, String slug, String scope,
                         List<MiniDocsVO.SearchHit> hits, int max) {
        for (MiniDocsVO.DocNode node : nodes) {
            if (hits.size() >= max) {
                return;
            }
            if ("doc".equals(node.type())
                    && (node.name().toLowerCase(Locale.ROOT).contains(needle)
                    || node.path().toLowerCase(Locale.ROOT).contains(needle))) {
                hits.add(new MiniDocsVO.SearchHit(stripSuffix(node.name()), node.path(),
                        bookName, org, slug, scope));
            }
            collect(node.children(), needle, bookName, org, slug, scope, hits, max);
        }
    }

    private static String stripSuffix(String name) {
        return name.toLowerCase(Locale.ROOT).endsWith(".md")
                ? name.substring(0, name.length() - 3) : name;
    }

    private static String safePath(String path) {
        if (!StringUtils.hasText(path)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "缺少文档路径");
        }
        String value = path.trim().replace('\\', '/');
        while (value.startsWith("/")) {
            value = value.substring(1);
        }
        if (value.contains("..") || value.isBlank()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "文档路径不合法");
        }
        return value.toLowerCase(Locale.ROOT).endsWith(".md") ? value : value + ".md";
    }

    private static void checkScope(String scope) {
        if (!SCOPE_MINE.equals(scope) && !SCOPE_PUBLIC.equals(scope)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "scope 只能是 mine 或 public");
        }
    }

    private static void requireSlug(String slug) {
        if (!StringUtils.hasText(slug)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "缺少知识库 slug");
        }
    }
}
