package cn.minims.desk.modules.life.contact.vo;

import java.time.LocalDate;

/**
 * 联系人展示对象。生日剩余天数在服务端算好，前端不用重复处理跨年。
 */
public record ContactVO(
        Long id,
        String name,
        String type,
        String company,
        String position,
        String phone,
        String email,
        String wechat,
        LocalDate birthday,
        Integer daysToBirthday,
        String note,
        String initial
) {
}
