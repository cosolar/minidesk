package cn.minims.desk.modules.life.contact.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 联系人：客户、同事、合作伙伴、家人。
 */
@Data
@TableName("contact")
public class ContactDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    /** client / colleague / partner / family */
    private String type;

    private String company;

    private String position;

    private String phone;

    private String email;

    private String wechat;

    private LocalDate birthday;

    private String note;

    private Long avatarFileId;

    private LocalDateTime createdAt;

    @TableLogic
    private Integer deleted;
}
