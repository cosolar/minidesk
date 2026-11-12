package cn.minims.desk.modules.life.lifelog.service;

import cn.minims.desk.common.api.ErrorCode;
import cn.minims.desk.common.api.PageResult;
import cn.minims.desk.common.exception.BizException;
import cn.minims.desk.modules.life.entity.LifeLogDO;
import cn.minims.desk.modules.life.mapper.LifeLogMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 生活记录 / 心情日记。
 */
@Service
public class LifeLogService {

    private final LifeLogMapper lifeLogMapper;

    public LifeLogService(LifeLogMapper lifeLogMapper) {
        this.lifeLogMapper = lifeLogMapper;
    }

    public PageResult<LifeLogDO> page(String month, long pageNo, long pageSize) {
        YearMonth ym = month == null || month.isBlank() ? YearMonth.now() : parseMonth(month);
        var query = Wrappers.lambdaQuery(LifeLogDO.class)
                .between(LifeLogDO::getLogDate, ym.atDay(1), ym.atEndOfMonth())
                .orderByDesc(LifeLogDO::getLogDate)
                .orderByDesc(LifeLogDO::getId);
        Page<LifeLogDO> result = lifeLogMapper.selectPage(Page.of(pageNo, pageSize), query);
        return PageResult.of(result.getRecords(), result.getTotal(), pageNo, pageSize);
    }

    @Transactional(rollbackFor = Exception.class)
    public LifeLogDO create(String title, String content, String mood, LocalDate logDate) {
        if (!StringUtils.hasText(title)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "请输入标题");
        }
        LifeLogDO log = new LifeLogDO();
        log.setTitle(title.trim());
        log.setContent(content);
        log.setMood(mood);
        log.setLogDate(logDate == null ? LocalDate.now() : logDate);
        log.setCreatedAt(LocalDateTime.now());
        log.setUpdatedAt(LocalDateTime.now());
        lifeLogMapper.insert(log);
        return log;
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, Map<String, Object> body) {
        LifeLogDO log = require(id);
        if (body.get("title") != null) {
            log.setTitle(String.valueOf(body.get("title")).trim());
        }
        if (body.get("content") != null) {
            log.setContent(String.valueOf(body.get("content")));
        }
        if (body.get("mood") != null) {
            log.setMood(String.valueOf(body.get("mood")));
        }
        if (body.get("logDate") != null) {
            log.setLogDate(LocalDate.parse(String.valueOf(body.get("logDate"))));
        }
        log.setUpdatedAt(LocalDateTime.now());
        lifeLogMapper.updateById(log);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        require(id);
        lifeLogMapper.deleteById(id);
    }

    public List<LifeLogDO> latest(int size) {
        return lifeLogMapper.selectList(Wrappers.lambdaQuery(LifeLogDO.class)
                .orderByDesc(LifeLogDO::getLogDate)
                .last("limit " + Math.min(Math.max(size, 1), 50)));
    }

    private LifeLogDO require(Long id) {
        LifeLogDO log = lifeLogMapper.selectById(id);
        if (log == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        return log;
    }

    private YearMonth parseMonth(String month) {
        try {
            return YearMonth.parse(month);
        } catch (RuntimeException ex) {
            throw new BizException(ErrorCode.PARAM_ERROR, "月份格式应为 yyyy-MM");
        }
    }
}
