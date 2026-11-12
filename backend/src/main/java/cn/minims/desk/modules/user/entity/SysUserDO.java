package cn.minims.desk.modules.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 用户。
 */
@Data
@TableName("sys_user")
public class SysUserDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String username;

    private String passwordHash;

    private String nickname;

    private String email;

    private Long avatarFileId;

    /** 1 正常 / 0 禁用 */
    private Integer status;

    /** self 自助注册 / admin 后台创建 / init 命令行 */
    private String source;

    private Integer pwdResetRequired;

    private Integer loginCount;

    private LocalDateTime lastLoginAt;

    private String lastLoginIp;

    private String remark;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
