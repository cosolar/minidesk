package cn.minims.desk.modules.system.file.vo;

/**
 * 上传结果。url 是可直接放进 img / a 标签的相对地址。
 */
public record FileVO(
        Long id,
        String name,
        long size,
        String mime,
        String url
) {
}
