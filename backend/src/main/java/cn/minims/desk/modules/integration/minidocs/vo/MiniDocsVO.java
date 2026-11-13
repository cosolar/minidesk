package cn.minims.desk.modules.integration.minidocs.vo;

import java.util.List;

/**
 * 知识库（MiniDocs）对接的对外视图对象。
 *
 * <p>统一「我的知识库」与「站点公开库」两种来源的差异，前端只认这一套结构。
 * 令牌明文不会出现在这里，也不会出现在日志里。
 */
public final class MiniDocsVO {

    private MiniDocsVO() {
    }

    /** 连接状态：未配置时也能返回，便于前端展示引导而不是弹错误 */
    public record Status(
            boolean configured,
            boolean enabled,
            boolean connected,
            String apiHost,
            String siteUrl,
            String username,
            String displayName,
            String defaultOrg,
            List<Tenant> tenants,
            String message
    ) {
    }

    /** 组织（MiniDocs 的 tenant），知识库接口路径里的 {org} 取自 slug */
    public record Tenant(String slug, String name, String role) {
    }

    /** 知识库卡片 */
    public record Book(
            String id,
            String name,
            String slug,
            String orgSlug,
            String description,
            String visibility,
            Integer docCount,
            List<String> tags,
            String updatedText,
            String coverSrc,
            String scope,
            String url
    ) {
    }

    public record BookPage(List<Book> list, long total, long page, long size) {
    }

    /** 目录树节点，与 MiniDocs 的 DocNode 对齐 */
    public record DocNode(
            String name,
            String path,
            String encodedPath,
            String type,
            long size,
            String modifiedAt,
            List<DocNode> children
    ) {
    }

    /**
     * 文档正文：我的知识库返回 markdown，公开库返回服务端渲染好的 html。
     * 二者互斥，前端按 markdown 是否为空选择渲染方式。
     */
    public record DocContent(
            String scope,
            String path,
            String name,
            String title,
            String markdown,
            String html,
            String modifiedAt,
            long size,
            String sourceUrl
    ) {
    }

    /** 站内文档检索命中（基于目录树的标题/路径匹配） */
    public record SearchHit(
            String title,
            String path,
            String bookName,
            String orgSlug,
            String slug,
            String scope
    ) {
    }

    /** 沉淀结果 */
    public record CreateResult(String path, String name, String url) {
    }
}
