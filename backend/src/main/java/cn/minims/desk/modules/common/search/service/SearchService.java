package cn.minims.desk.modules.common.search.service;

import cn.minims.desk.framework.tenant.TenantContext;
import cn.minims.desk.modules.common.search.vo.SearchResultVO;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 全局搜索。
 *
 * <p>用 LIKE 而不是全文索引：单用户五年的数据量在几万行级，LIKE 的毫秒级响应足够；
 * 引入 FTS5 会带来额外建表与同步成本，等真的慢了再上（见数据库设计 §4.2）。
 *
 * <p>所有查询显式带 {@code user_id}——这里走 JdbcTemplate，MyBatis 的租户插件不生效。
 */
@Service
public class SearchService {

    /** 每个域最多返回条数，避免一屏全是笔记 */
    private static final int PER_GROUP = 6;

    private final JdbcTemplate jdbcTemplate;

    public SearchService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public SearchResultVO search(String keyword) {
        if (!StringUtils.hasText(keyword) || keyword.trim().length() < 1) {
            return new SearchResultVO("", 0, List.of());
        }
        String kw = keyword.trim();
        long userId = TenantContext.requireUserId();
        String like = "%" + kw + "%";

        List<SearchResultVO.Group> groups = new ArrayList<>();

        add(groups, "task", "任务", query(userId,
                "select id, title, coalesce(note, '') as body, "
                        + "coalesce(due_date, '') as meta from task "
                        + "where user_id = ? and deleted = 0 and (title like ? or note like ?) "
                        + "order by case when status = 'done' then 1 else 0 end, due_date desc, id desc limit " + PER_GROUP,
                like, like), "/todo");

        add(groups, "note", "笔记", query(userId,
                "select id, title, coalesce(content, '') as body, "
                        + "coalesce(substr(updated_at, 1, 10), '') as meta from note "
                        + "where user_id = ? and deleted = 0 and (title like ? or content like ?) "
                        + "order by updated_at desc limit " + PER_GROUP,
                like, like), "/note");

        add(groups, "project", "项目", query(userId,
                "select id, name as title, coalesce(description, '') as body, "
                        + "coalesce(end_date, '') as meta from project "
                        + "where user_id = ? and deleted = 0 and (name like ? or description like ?) "
                        + "order by id desc limit " + PER_GROUP,
                like, like), "/work/project");

        add(groups, "journal", "日志", query(userId,
                "select id, title, coalesce(content, '') as body, "
                        + "coalesce(substr(period_start, 1, 10), '') as meta from journal "
                        + "where user_id = ? and deleted = 0 and (title like ? or content like ?) "
                        + "order by period_start desc limit " + PER_GROUP,
                like, like), "/work/journal");

        add(groups, "asset", "资产", query(userId,
                "select id, title, coalesce(description, coalesce(url, '')) as body, "
                        + "coalesce(category, '') as meta from doc_asset "
                        + "where user_id = ? and deleted = 0 and (title like ? or description like ? or url like ?) "
                        + "order by updated_at desc limit " + PER_GROUP,
                like, like, like), "/work/asset");

        add(groups, "meeting", "会议纪要", query(userId,
                "select id, title, coalesce(content, '') as body, "
                        + "coalesce(meeting_date, '') as meta from meeting "
                        + "where user_id = ? and deleted = 0 and (title like ? or content like ?) "
                        + "order by meeting_date desc limit " + PER_GROUP,
                like, like), "/work/meeting");

        add(groups, "contact", "联系人", query(userId,
                "select id, name as title, "
                        + "trim(coalesce(company, '') || ' ' || coalesce(phone, '') || ' ' || coalesce(wechat, '')) as body, "
                        + "coalesce(birthday, '') as meta from contact "
                        + "where user_id = ? and deleted = 0 "
                        + "and (name like ? or company like ? or phone like ? or wechat like ?) "
                        + "order by name limit " + PER_GROUP,
                like, like, like, like), "/life/contact");

        add(groups, "ledger", "记账", query(userId,
                "select id, coalesce(remark, '未命名') as title, "
                        + "case when type = 'income' then '收入' else '支出' end as body, "
                        + "occur_date as meta from ledger "
                        + "where user_id = ? and deleted = 0 and (remark like ? or tags like ?) "
                        + "order by occur_date desc limit " + PER_GROUP,
                like, like), "/life/ledger");

        add(groups, "plan", "学习计划", query(userId,
                "select id, title, coalesce(status, '') as body, coalesce(end_date, '') as meta "
                        + "from study_plan where user_id = ? and title like ? "
                        + "order by level asc limit " + PER_GROUP,
                like), "/study/plan");

        add(groups, "nav", "导航", query(userId,
                "select id, title, coalesce(description, url) as body, "
                        + "case when module = 'study' then '学习资源' else '工作工具' end as meta "
                        + "from nav_link where user_id = ? and deleted = 0 "
                        + "and (title like ? or description like ? or url like ?) limit " + PER_GROUP,
                like, like, like), "/work/tool");

        int total = groups.stream().mapToInt(group -> group.hits().size()).sum();
        return new SearchResultVO(kw, total, groups);
    }

    private List<SearchResultVO.Hit> query(long userId, String sql, Object... likeParams) {
        List<Object> params = new ArrayList<>();
        params.add(userId);
        params.addAll(java.util.Arrays.asList(likeParams));
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, params.toArray());
        List<SearchResultVO.Hit> hits = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            hits.add(new SearchResultVO.Hit(
                    "",
                    String.valueOf(row.get("id")),
                    String.valueOf(row.get("title")),
                    snippet(String.valueOf(row.getOrDefault("body", ""))),
                    row.get("meta") == null ? "" : String.valueOf(row.get("meta")),
                    ""));
        }
        return hits;
    }

    private void add(List<SearchResultVO.Group> groups, String key, String label,
                     List<SearchResultVO.Hit> hits, String route) {
        if (hits.isEmpty()) {
            return;
        }
        List<SearchResultVO.Hit> mapped = hits.stream()
                .map(hit -> new SearchResultVO.Hit(key, hit.id(), hit.title(), hit.snippet(), hit.meta(), route))
                .toList();
        groups.add(new SearchResultVO.Group(key, label, mapped));
    }

    /** 摘要：去掉 Markdown 标记与换行，取前 70 字 */
    private String snippet(String body) {
        if (!StringUtils.hasText(body)) {
            return "";
        }
        String text = body.replaceAll("[#>*`\\-\\[\\]()]", " ")
                .replaceAll("\\s+", " ")
                .trim();
        return text.length() > 70 ? text.substring(0, 70) + "…" : text;
    }
}
