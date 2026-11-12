package cn.minims.desk.modules.system.mail.service;

import cn.minims.desk.common.api.ErrorCode;
import cn.minims.desk.common.exception.BizException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

/**
 * 邮件发送。只做两件事：验证码邮件、测试邮件。
 */
@Service
public class MailService {

    private static final Logger log = LoggerFactory.getLogger(MailService.class);

    private final MailConfigService configService;

    public MailService(MailConfigService configService) {
        this.configService = configService;
    }

    public void sendCode(String to, String code, String siteName) {
        String subject = siteName + " 注册验证码";
        String html = """
                <div style="font-family:-apple-system,'PingFang SC','Microsoft YaHei',sans-serif;
                            max-width:480px;margin:0 auto;padding:28px 24px;border-radius:16px;
                            background:#f7fbf9;color:#22383c;">
                  <h2 style="margin:0 0 8px;font-size:18px;">%s</h2>
                  <p style="margin:0 0 18px;color:#5b7078;font-size:13px;">你正在注册账号，验证码 10 分钟内有效。</p>
                  <div style="font-size:30px;letter-spacing:8px;font-weight:700;color:#23a981;
                              background:#fff;border-radius:12px;padding:14px 0;text-align:center;">%s</div>
                  <p style="margin:18px 0 0;color:#93a5a0;font-size:12px;">如果这不是你本人的操作，请忽略这封邮件。</p>
                </div>
                """.formatted(siteName, code);
        send(to, subject, html);
    }

    public void sendTest(String to, String siteName) {
        String html = """
                <div style="font-family:-apple-system,'PingFang SC','Microsoft YaHei',sans-serif;
                            max-width:480px;margin:0 auto;padding:24px;border-radius:14px;background:#f7fbf9;">
                  <h2 style="margin:0 0 8px;font-size:17px;color:#22383c;">%s 邮件配置正常</h2>
                  <p style="margin:0;color:#5b7078;font-size:13px;">
                    收到这封邮件说明 SMTP 配置可用，注册验证码可以正常发送。
                  </p>
                </div>
                """.formatted(siteName);
        send(to, siteName + " 邮件配置测试", html);
    }

    private void send(String to, String subject, String html) {
        JavaMailSender sender = configService.sender();
        try {
            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setFrom(configService.fromAddress());
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(html, true);
            sender.send(message);
            log.info("mail sent, to={}, subject={}", to, subject);
        } catch (BizException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("mail send failed, to={}", to, ex);
            throw new BizException(ErrorCode.MAIL_SEND_FAILED, "邮件发送失败：" + ex.getMessage());
        }
    }
}
