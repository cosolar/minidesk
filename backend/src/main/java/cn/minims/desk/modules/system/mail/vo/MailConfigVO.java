package cn.minims.desk.modules.system.mail.vo;

/**
 * 发件邮箱配置。密码只回显掩码，形如 ****abcd。
 */
public record MailConfigVO(
        String host,
        int port,
        String username,
        String from,
        boolean ssl,
        String maskedPassword,
        boolean configured,
        boolean enabled
) {
}
