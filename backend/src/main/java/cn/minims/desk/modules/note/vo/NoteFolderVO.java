package cn.minims.desk.modules.note.vo;

import cn.minims.desk.modules.note.entity.NoteFolderDO;

/**
 * 目录节点。前端按 parentId 自行拼树，避免后端递归组装。
 */
public record NoteFolderVO(
        String id,
        String name,
        String module,
        String parentId
) {
    public static NoteFolderVO from(NoteFolderDO folder) {
        return new NoteFolderVO(
                String.valueOf(folder.getId()),
                folder.getName(),
                folder.getModule(),
                folder.getParentId() == null ? null : String.valueOf(folder.getParentId()));
    }
}
