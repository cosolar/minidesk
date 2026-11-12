package cn.minims.desk.modules.work.docasset.service;

import cn.minims.desk.common.api.ErrorCode;
import cn.minims.desk.common.exception.BizException;
import cn.minims.desk.modules.work.docasset.dto.DocAssetSaveDTO;
import cn.minims.desk.modules.work.docasset.entity.DocAssetDO;
import cn.minims.desk.modules.work.docasset.mapper.DocAssetMapper;
import cn.minims.desk.modules.work.docasset.vo.DocAssetVO;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 文档资产业务。
 */
@Service
public class DocAssetService {

    private static final Set<String> TYPES = Set.of("markdown", "link", "file");

    private final DocAssetMapper mapper;

    public DocAssetService(DocAssetMapper mapper) {
        this.mapper = mapper;
    }

    public List<DocAssetVO> list(String docType, String category, String keyword) {
        var query = Wrappers.lambdaQuery(DocAssetDO.class);
        if (StringUtils.hasText(docType)) {
            query.eq(DocAssetDO::getDocType, docType);
        }
        if (StringUtils.hasText(category)) {
            query.eq(DocAssetDO::getCategory, category);
        }
        if (StringUtils.hasText(keyword)) {
            query.and(w -> w.like(DocAssetDO::getTitle, keyword.trim())
                    .or().like(DocAssetDO::getDescription, keyword.trim()));
        }
        query.orderByDesc(DocAssetDO::getPinned).orderByDesc(DocAssetDO::getUpdatedAt);
        return mapper.selectList(query).stream().map(item -> DocAssetVO.from(item, false)).toList();
    }

    public DocAssetVO detail(Long id) {
        DocAssetDO item = mapper.selectById(id);
        if (item == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        return DocAssetVO.from(item, true);
    }

    @Transactional(rollbackFor = Exception.class)
    public DocAssetVO create(DocAssetSaveDTO dto) {
        DocAssetDO item = new DocAssetDO();
        apply(item, dto, true);
        item.setCreatedAt(LocalDateTime.now());
        item.setUpdatedAt(LocalDateTime.now());
        mapper.insert(item);
        return DocAssetVO.from(item, true);
    }

    @Transactional(rollbackFor = Exception.class)
    public DocAssetVO update(Long id, DocAssetSaveDTO dto) {
        DocAssetDO item = mapper.selectById(id);
        if (item == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        apply(item, dto, false);
        item.setUpdatedAt(LocalDateTime.now());
        mapper.updateById(item);
        return DocAssetVO.from(item, true);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        if (mapper.deleteById(id) == 0) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
    }

    private void apply(DocAssetDO item, DocAssetSaveDTO dto, boolean creating) {
        item.setTitle(dto.title().trim());
        String type = TYPES.contains(dto.docType()) ? dto.docType() : "markdown";
        item.setDocType(type);
        item.setProvider(dto.provider());
        item.setUrl(dto.url());
        item.setFileId(dto.fileId());
        item.setCategory(dto.category());
        item.setTags(dto.tags());
        item.setDescription(dto.description());
        item.setPinned(Boolean.TRUE.equals(dto.pinned()) ? 1 : 0);
        if (dto.content() != null) {
            item.setContent(dto.content());
        }
        if ("link".equals(type) && !StringUtils.hasText(dto.url())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "外链文档必须填写地址");
        }
    }
}
