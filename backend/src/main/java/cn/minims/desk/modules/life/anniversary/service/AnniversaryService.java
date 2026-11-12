package cn.minims.desk.modules.life.anniversary.service;

import cn.minims.desk.common.api.ErrorCode;
import cn.minims.desk.common.exception.BizException;
import cn.minims.desk.modules.common.calendar.entity.AnniversaryDO;
import cn.minims.desk.modules.common.calendar.entity.CountdownDO;
import cn.minims.desk.modules.common.calendar.mapper.AnniversaryMapper;
import cn.minims.desk.modules.common.calendar.mapper.CountdownMapper;
import cn.minims.desk.modules.life.anniversary.vo.AnniversaryVO;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 纪念日与倒数日。日期按「下一次发生」计算剩余天数，跨年纪念日自动滚到明年。
 */
@Service
public class AnniversaryService {

    private final AnniversaryMapper anniversaryMapper;
    private final CountdownMapper countdownMapper;

    public AnniversaryService(AnniversaryMapper anniversaryMapper, CountdownMapper countdownMapper) {
        this.anniversaryMapper = anniversaryMapper;
        this.countdownMapper = countdownMapper;
    }

    public List<AnniversaryVO> listAnniversaries() {
        LocalDate today = LocalDate.now();
        return anniversaryMapper.selectList(Wrappers.lambdaQuery(AnniversaryDO.class)
                        .orderByAsc(AnniversaryDO::getDate))
                .stream()
                .map(item -> {
                    LocalDate next = nextOccurrence(item.getDate(), item.getRepeatYearly());
                    return new AnniversaryVO(
                            String.valueOf(item.getId()),
                            item.getTitle(),
                            item.getType(),
                            item.getPerson(),
                            item.getDate().toString(),
                            item.getRepeatYearly() != null && item.getRepeatYearly() == 1,
                            item.getAdvanceDays() == null ? 3 : item.getAdvanceDays(),
                            next.toString(),
                            ChronoUnit.DAYS.between(today, next));
                })
                .sorted(Comparator.comparingLong(AnniversaryVO::daysLeft))
                .toList();
    }

    public List<AnniversaryVO.CountdownVO> listCountdowns() {
        LocalDate today = LocalDate.now();
        return countdownMapper.selectList(Wrappers.lambdaQuery(CountdownDO.class)
                        .orderByAsc(CountdownDO::getTargetDate))
                .stream()
                .map(item -> new AnniversaryVO.CountdownVO(
                        String.valueOf(item.getId()),
                        item.getTitle(),
                        item.getTargetDate().toString(),
                        item.getType(),
                        item.getColor(),
                        ChronoUnit.DAYS.between(today, item.getTargetDate())))
                .toList();
    }

    @Transactional(rollbackFor = Exception.class)
    public AnniversaryVO createAnniversary(String title, String type, String person,
                                          LocalDate date, Integer advanceDays, Boolean repeatYearly) {
        if (!StringUtils.hasText(title) || date == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "请填写名称与日期");
        }
        AnniversaryDO item = new AnniversaryDO();
        item.setTitle(title.trim());
        item.setType(StringUtils.hasText(type) ? type : "birthday");
        item.setPerson(person);
        item.setDate(date);
        item.setLunar(0);
        item.setRepeatYearly(Boolean.FALSE.equals(repeatYearly) ? 0 : 1);
        item.setAdvanceDays(advanceDays == null ? 3 : Math.max(advanceDays, 0));
        item.setCreatedAt(LocalDateTime.now());
        anniversaryMapper.insert(item);
        return listAnniversaries().stream()
                .filter(vo -> vo.id().equals(String.valueOf(item.getId())))
                .findFirst()
                .orElseThrow();
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteAnniversary(Long id) {
        anniversaryMapper.deleteById(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public AnniversaryVO.CountdownVO createCountdown(String title, LocalDate targetDate, String type, String color) {
        if (!StringUtils.hasText(title) || targetDate == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "请填写名称与目标日期");
        }
        CountdownDO item = new CountdownDO();
        item.setTitle(title.trim());
        item.setTargetDate(targetDate);
        item.setType(StringUtils.hasText(type) ? type : "custom");
        item.setColor(StringUtils.hasText(color) ? color : "#FF6B6B");
        item.setPinned(0);
        item.setCreatedAt(LocalDateTime.now());
        countdownMapper.insert(item);
        return listCountdowns().stream()
                .filter(vo -> vo.id().equals(String.valueOf(item.getId())))
                .findFirst()
                .orElseThrow();
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteCountdown(Long id) {
        countdownMapper.deleteById(id);
    }

    /** 跨年重复的纪念日：今年已过则滚到明年 */
    private LocalDate nextOccurrence(LocalDate date, Integer repeatYearly) {
        LocalDate today = LocalDate.now();
        if (repeatYearly == null || repeatYearly == 0) {
            return date;
        }
        LocalDate candidate = date.withYear(today.getYear());
        if (candidate.isBefore(today)) {
            candidate = candidate.plusYears(1);
        }
        return candidate;
    }
}
