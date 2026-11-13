package cn.minims.desk.framework.satoken;

/**
 * 权限码常量。命名：模块:资源:动作。
 * 新增写接口必须在这里登记，并同步授权种子。
 */
public final class PermissionCodes {

    public static final String ALL = "*";

    public static final String WORK_TASK_ADD = "work:task:add";
    public static final String WORK_TASK_UPDATE = "work:task:update";
    public static final String WORK_TASK_DELETE = "work:task:delete";

    public static final String STUDY_RECORD_ADD = "study:record:add";
    public static final String LIFE_LEDGER_ADD = "life:ledger:add";

    public static final String WORK_JOURNAL_MANAGE = "work:journal:manage";
    public static final String WORK_DOC_MANAGE = "work:doc:manage";
    public static final String WORK_MEETING_MANAGE = "work:meeting:manage";

    /** 导航链接：工作「书签」与学习「资源」共用 */
    public static final String SHARED_NAV_MANAGE = "shared:nav:manage";
    public static final String SHARED_CALENDAR_MANAGE = "shared:calendar:manage";

    public static final String LIFE_CONTACT_MANAGE = "life:contact:manage";
    public static final String LIFE_ALBUM_MANAGE = "life:album:manage";

    /** 笔记跨板块共用 */
    public static final String NOTE_MANAGE = "shared:note:manage";

    /** 打卡、纪念日、购物清单等生活工具 */
    public static final String HABIT_MANAGE = "shared:habit:manage";
    public static final String ANNIVERSARY_MANAGE = "life:anniversary:manage";
    public static final String SHOPPING_MANAGE = "life:shopping:manage";

    public static final String SYSTEM_USER_MANAGE = "system:user:manage";
    public static final String SYSTEM_ROLE_MANAGE = "system:role:manage";
    public static final String SYSTEM_SETTING_UPDATE = "system:setting:update";
    public static final String SYSTEM_AUDIT_QUERY = "system:audit:query";

    private PermissionCodes() {
    }
}
