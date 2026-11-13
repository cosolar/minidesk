package cn.minims.desk.modules.integration.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 第三方集成配置（和风天气 / MiniDocs 知识库）。
 * apiKeyCipher 存 AES-GCM 密文，明文只在保存瞬间存在于内存。
 */
@Data
@TableName("integration_config")
public class IntegrationConfigDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String provider;

    private String apiKeyCipher;

    private String apiHost;

    private String extraJson;

    private Integer enabled;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
