package cn.minims.desk.framework.tenant;

import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import java.util.Set;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import org.springframework.stereotype.Component;

/**
 * 多账号数据隔离：自动为 SQL 追加 user_id 条件，业务代码不手写。
 *
 * <p>忽略两类表：全局表（无 user_id 列）与显式 ignore 的上下文（登录、注册、定时任务）。
 */
@Component
public class UserTenantHandler implements TenantLineHandler {

    /** 全局表：不含 user_id，或由管理员跨用户访问 */
    private static final Set<String> GLOBAL_TABLES = Set.of(
            "sys_user",
            "sys_role",
            "sys_user_role",
            "sys_role_permission",
            "sys_config",
            "sys_login_log",
            "sys_oper_log",
            "job_exec_log",
            "backup_record",
            "qrtz_job_details",
            "qrtz_triggers",
            "qrtz_simple_triggers",
            "qrtz_cron_triggers",
            "qrtz_simpsprop_triggers",
            "qrtz_blob_triggers",
            "qrtz_calendars",
            "qrtz_paused_trigger_grps",
            "qrtz_fired_triggers",
            "qrtz_scheduler_state",
            "qrtz_locks"
    );

    @Override
    public Expression getTenantId() {
        return new LongValue(TenantContext.requireUserId());
    }

    @Override
    public String getTenantIdColumn() {
        return "user_id";
    }

    @Override
    public boolean ignoreTable(String tableName) {
        if (TenantContext.isIgnored()) {
            return true;
        }
        return GLOBAL_TABLES.contains(tableName.toLowerCase());
    }
}
