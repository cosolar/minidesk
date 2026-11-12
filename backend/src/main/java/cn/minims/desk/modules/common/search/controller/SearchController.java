package cn.minims.desk.modules.common.search.controller;

import cn.minims.desk.common.api.Result;
import cn.minims.desk.modules.common.search.service.SearchService;
import cn.minims.desk.modules.common.search.vo.SearchResultVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 全局搜索：一次请求返回所有模块的命中，按域分组。
 */
@RestController
@RequestMapping("/api/search")
public class SearchController {

    private final SearchService searchService;

    public SearchController(SearchService searchService) {
        this.searchService = searchService;
    }

    @GetMapping
    public Result<SearchResultVO> search(@RequestParam String keyword) {
        return Result.ok(searchService.search(keyword));
    }
}
