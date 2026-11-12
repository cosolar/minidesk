package cn.minims.desk.modules.life.contact.service;

import cn.minims.desk.common.api.ErrorCode;
import cn.minims.desk.common.exception.BizException;
import cn.minims.desk.modules.life.contact.dto.ContactSaveDTO;
import cn.minims.desk.modules.life.contact.entity.ContactDO;
import cn.minims.desk.modules.life.contact.mapper.ContactMapper;
import cn.minims.desk.modules.life.contact.vo.ContactVO;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 联系人。
 */
@Service
public class ContactService {

    private final ContactMapper mapper;

    public ContactService(ContactMapper mapper) {
        this.mapper = mapper;
    }

    public List<ContactVO> list(String keyword, String type) {
        var query = Wrappers.lambdaQuery(ContactDO.class);
        if (StringUtils.hasText(type)) {
            query.eq(ContactDO::getType, type);
        }
        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim();
            query.and(w -> w.like(ContactDO::getName, kw)
                    .or().like(ContactDO::getCompany, kw)
                    .or().like(ContactDO::getPhone, kw));
        }
        query.orderByAsc(ContactDO::getName);
        return mapper.selectList(query).stream().map(this::toVO).toList();
    }

    @Transactional(rollbackFor = Exception.class)
    public ContactVO create(ContactSaveDTO dto) {
        ContactDO item = new ContactDO();
        apply(item, dto);
        item.setCreatedAt(LocalDateTime.now());
        mapper.insert(item);
        return toVO(item);
    }

    @Transactional(rollbackFor = Exception.class)
    public ContactVO update(Long id, ContactSaveDTO dto) {
        ContactDO item = require(id);
        apply(item, dto);
        mapper.updateById(item);
        return toVO(item);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        require(id);
        mapper.deleteById(id);
    }

    private void apply(ContactDO item, ContactSaveDTO dto) {
        item.setName(dto.name().trim());
        item.setType(StringUtils.hasText(dto.type()) ? dto.type() : "client");
        item.setCompany(dto.company());
        item.setPosition(dto.position());
        item.setPhone(dto.phone());
        item.setEmail(dto.email());
        item.setWechat(dto.wechat());
        item.setBirthday(dto.birthday());
        item.setNote(dto.note());
    }

    private ContactDO require(Long id) {
        ContactDO item = mapper.selectById(id);
        if (item == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        return item;
    }

    private ContactVO toVO(ContactDO item) {
        Integer days = null;
        if (item.getBirthday() != null) {
            LocalDate today = LocalDate.now();
            LocalDate next = item.getBirthday().withYear(today.getYear());
            if (next.isBefore(today)) {
                next = next.plusYears(1);
            }
            days = (int) ChronoUnit.DAYS.between(today, next);
        }
        String initial = StringUtils.hasText(item.getName())
                ? item.getName().substring(0, 1).toUpperCase() : "#";
        return new ContactVO(item.getId(), item.getName(), item.getType(), item.getCompany(),
                item.getPosition(), item.getPhone(), item.getEmail(), item.getWechat(),
                item.getBirthday(), days, item.getNote(), initial);
    }
}
