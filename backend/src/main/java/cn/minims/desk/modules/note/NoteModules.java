package cn.minims.desk.modules.note;

import java.util.Set;

/**
 * 笔记板块常量。工作 / 学习 / 生活是笔记库的三个固定根目录，不入库。
 */
public final class NoteModules {

    public static final String WORK = "work";
    public static final String STUDY = "study";
    public static final String LIFE = "life";

    public static final Set<String> ALL = Set.of(WORK, STUDY, LIFE);

    private NoteModules() {
    }

    public static boolean supports(String module) {
        return module != null && ALL.contains(module);
    }
}
