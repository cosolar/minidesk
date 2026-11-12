package cn.minims.desk.modules.study.plan.vo;

import java.util.List;

/**
 * 学习计划节点。progress 为 0-100；周计划按任务完成率，月/年按子节点平均。
 */
public record StudyPlanVO(
        String id,
        String parentId,
        int level,
        String title,
        double targetHours,
        int progress,
        long taskTotal,
        long taskDone,
        String startDate,
        String endDate,
        String status,
        List<StudyPlanVO> children
) {
    public StudyPlanVO withProgress(int value) {
        return new StudyPlanVO(id, parentId, level, title, targetHours, value,
                taskTotal, taskDone, startDate, endDate, status, children);
    }
}
