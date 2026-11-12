package cn.minims.desk.modules.work.project.service;

import cn.minims.desk.common.api.ErrorCode;
import cn.minims.desk.common.exception.BizException;
import cn.minims.desk.modules.work.project.dto.ProjectSaveDTO;
import cn.minims.desk.modules.work.project.entity.ProjectDO;
import cn.minims.desk.modules.work.project.mapper.ProjectMapper;
import cn.minims.desk.modules.work.project.vo.ProjectVO;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 项目业务。进度 = 已完成任务 / 总任务，一次性聚合，避免逐项目查询。
 */
@Service
public class ProjectService {

    private final ProjectMapper projectMapper;

    public ProjectService(ProjectMapper projectMapper) {
        this.projectMapper = projectMapper;
    }

    public List<ProjectVO> list(String status, String keyword) {
        var query = Wrappers.lambdaQuery(ProjectDO.class);
        if (StringUtils.hasText(status)) {
            query.eq(ProjectDO::getStatus, status);
        }
        if (StringUtils.hasText(keyword)) {
            query.like(ProjectDO::getName, keyword.trim());
        }
        query.orderByAsc(ProjectDO::getSortOrder).orderByDesc(ProjectDO::getId);
        List<ProjectDO> projects = projectMapper.selectList(query);

        Map<Long, long[]> counts = new HashMap<>();
        for (Map<String, Object> row : projectMapper.countTasksByStatus()) {
            Long projectId = ((Number) row.get("projectId")).longValue();
            String statusValue = String.valueOf(row.get("status"));
            long cnt = ((Number) row.get("cnt")).longValue();
            long[] perStatus = counts.computeIfAbsent(projectId, key -> new long[3]);
            if ("done".equals(statusValue)) {
                perStatus[1] += cnt;
            } else {
                perStatus[2] += cnt;
            }
            perStatus[0] += cnt;
        }

        return projects.stream().map(project -> {
            long[] perStatus = counts.getOrDefault(project.getId(), new long[3]);
            long total = perStatus[0];
            long done = perStatus[1];
            int progress = total == 0 ? 0 : (int) Math.round(done * 100.0 / total);
            long overdue = projectMapper.countOverdue(project.getId());
            return new ProjectVO(
                    String.valueOf(project.getId()),
                    project.getName(),
                    project.getDescription(),
                    project.getColor(),
                    project.getStatus(),
                    project.getPriority(),
                    project.getStartDate(),
                    project.getEndDate(),
                    progress,
                    new ProjectVO.Stats(total, done, perStatus[2], overdue));
        }).toList();
    }

    public ProjectVO detail(Long id) {
        ProjectDO project = require(id);
        long total = 0;
        long done = 0;
        long doing = 0;
        for (Map<String, Object> row : projectMapper.countTasksByStatus()) {
            if (((Number) row.get("projectId")).longValue() != id) {
                continue;
            }
            long cnt = ((Number) row.get("cnt")).longValue();
            total += cnt;
            if ("done".equals(String.valueOf(row.get("status")))) {
                done += cnt;
            } else {
                doing += cnt;
            }
        }
        int progress = total == 0 ? 0 : (int) Math.round(done * 100.0 / total);
        return new ProjectVO(
                String.valueOf(project.getId()),
                project.getName(),
                project.getDescription(),
                project.getColor(),
                project.getStatus(),
                project.getPriority(),
                project.getStartDate(),
                project.getEndDate(),
                progress,
                new ProjectVO.Stats(total, done, doing, projectMapper.countOverdue(id)));
    }

    @Transactional(rollbackFor = Exception.class)
    public ProjectVO create(ProjectSaveDTO dto) {
        ProjectDO project = new ProjectDO();
        apply(project, dto);
        project.setStatus(StringUtils.hasText(dto.status()) ? dto.status() : "doing");
        project.setPriority(dto.priority() == null ? 2 : dto.priority());
        project.setProgressCache(0);
        project.setSortOrder(0);
        project.setCreatedAt(LocalDateTime.now());
        project.setUpdatedAt(LocalDateTime.now());
        projectMapper.insert(project);
        return detail(project.getId());
    }

    @Transactional(rollbackFor = Exception.class)
    public ProjectVO update(Long id, ProjectSaveDTO dto) {
        ProjectDO project = require(id);
        apply(project, dto);
        if (StringUtils.hasText(dto.status())) {
            project.setStatus(dto.status());
        }
        if (dto.priority() != null) {
            project.setPriority(dto.priority());
        }
        project.setUpdatedAt(LocalDateTime.now());
        projectMapper.updateById(project);
        return detail(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        require(id);
        projectMapper.deleteById(id);
    }

    private ProjectDO require(Long id) {
        ProjectDO project = projectMapper.selectById(id);
        if (project == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        return project;
    }

    private void apply(ProjectDO project, ProjectSaveDTO dto) {
        project.setName(dto.name().trim());
        project.setDescription(dto.description());
        project.setColor(StringUtils.hasText(dto.color()) ? dto.color() : "#4A86E8");
        project.setStartDate(dto.startDate());
        project.setEndDate(dto.endDate());
        project.setGroupId(dto.groupId());
    }
}
