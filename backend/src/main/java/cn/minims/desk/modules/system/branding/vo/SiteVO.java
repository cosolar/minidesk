package cn.minims.desk.modules.system.branding.vo;

/**
 * 站点公开信息（登录页与侧栏共用）。
 * logoUrl / logoWideUrl 为 null 时前端回退到内置资源。
 */
public record SiteVO(
        String siteName,
        String logoUrl,
        String logoWideUrl
) {
}
