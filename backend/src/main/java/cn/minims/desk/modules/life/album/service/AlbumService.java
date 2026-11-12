package cn.minims.desk.modules.life.album.service;

import cn.minims.desk.common.api.ErrorCode;
import cn.minims.desk.common.exception.BizException;
import cn.minims.desk.modules.life.album.dto.AlbumSaveDTO;
import cn.minims.desk.modules.life.album.entity.AlbumDO;
import cn.minims.desk.modules.life.album.entity.PhotoDO;
import cn.minims.desk.modules.life.album.mapper.AlbumMapper;
import cn.minims.desk.modules.life.album.mapper.PhotoMapper;
import cn.minims.desk.modules.life.album.vo.AlbumVO;
import cn.minims.desk.modules.system.file.service.FileService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 相册。照片可以本地上传，也可以直接贴图床地址——两者都是「一个 URL」，
 * 因此存储层不区分来源，只在 fileId 里保留本地文件的引用用于清理。
 */
@Service
public class AlbumService {

    private final AlbumMapper albumMapper;
    private final PhotoMapper photoMapper;
    private final FileService fileService;

    public AlbumService(AlbumMapper albumMapper, PhotoMapper photoMapper, FileService fileService) {
        this.albumMapper = albumMapper;
        this.photoMapper = photoMapper;
        this.fileService = fileService;
    }

    public List<AlbumVO> albums() {
        List<AlbumDO> albums = albumMapper.selectList(Wrappers.lambdaQuery(AlbumDO.class)
                .orderByDesc(AlbumDO::getUpdatedAt).orderByDesc(AlbumDO::getId));
        return albums.stream().map(album -> {
            long count = photoMapper.selectCount(Wrappers.lambdaQuery(PhotoDO.class)
                    .eq(PhotoDO::getAlbumId, album.getId()));
            return new AlbumVO(album.getId(), album.getName(), album.getDescription(),
                    album.getCoverUrl(), count, album.getCreatedAt(), null);
        }).toList();
    }

    public AlbumVO detail(Long id) {
        AlbumDO album = require(id);
        List<AlbumVO.PhotoVO> photos = photos(id);
        return new AlbumVO(album.getId(), album.getName(), album.getDescription(), album.getCoverUrl(),
                photos.size(), album.getCreatedAt(), photos);
    }

    public List<AlbumVO.PhotoVO> photos(Long albumId) {
        return photoMapper.selectList(Wrappers.lambdaQuery(PhotoDO.class)
                        .eq(PhotoDO::getAlbumId, albumId)
                        .orderByAsc(PhotoDO::getSortOrder)
                        .orderByDesc(PhotoDO::getId))
                .stream()
                .map(p -> new AlbumVO.PhotoVO(p.getId(), p.getAlbumId(), p.getTitle(), p.getUrl(),
                        p.getFileId(), p.getTakenAt()))
                .toList();
    }

    @Transactional(rollbackFor = Exception.class)
    public AlbumVO create(AlbumSaveDTO dto) {
        AlbumDO album = new AlbumDO();
        album.setName(dto.name().trim());
        album.setDescription(dto.description());
        album.setCreatedAt(LocalDateTime.now());
        album.setUpdatedAt(LocalDateTime.now());
        albumMapper.insert(album);
        return detail(album.getId());
    }

    @Transactional(rollbackFor = Exception.class)
    public AlbumVO update(Long id, AlbumSaveDTO dto) {
        AlbumDO album = require(id);
        album.setName(dto.name().trim());
        album.setDescription(dto.description());
        album.setUpdatedAt(LocalDateTime.now());
        albumMapper.updateById(album);
        return detail(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        require(id);
        albumMapper.deleteById(id);
        photoMapper.delete(Wrappers.lambdaQuery(PhotoDO.class).eq(PhotoDO::getAlbumId, id));
    }

    @Transactional(rollbackFor = Exception.class)
    public AlbumVO.PhotoVO addPhoto(Long albumId, AlbumSaveDTO.PhotoSaveDTO dto) {
        AlbumDO album = require(albumId);
        String url = dto.url();
        if (!StringUtils.hasText(url) && dto.fileId() != null) {
            url = fileService.urlOf(dto.fileId());
        }
        if (!StringUtils.hasText(url)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "请上传图片或填写图片地址");
        }

        PhotoDO photo = new PhotoDO();
        photo.setAlbumId(albumId);
        photo.setTitle(dto.title());
        photo.setUrl(url.trim());
        photo.setFileId(dto.fileId());
        photo.setTakenAt(dto.takenAt());
        photo.setSortOrder(0);
        photo.setCreatedAt(LocalDateTime.now());
        photoMapper.insert(photo);

        // 第一张照片自动成为封面
        if (!StringUtils.hasText(album.getCoverUrl())) {
            album.setCoverUrl(photo.getUrl());
            album.setUpdatedAt(LocalDateTime.now());
            albumMapper.updateById(album);
        }
        return new AlbumVO.PhotoVO(photo.getId(), albumId, photo.getTitle(), photo.getUrl(),
                photo.getFileId(), photo.getTakenAt());
    }

    @Transactional(rollbackFor = Exception.class)
    public void deletePhoto(Long photoId) {
        PhotoDO photo = photoMapper.selectById(photoId);
        if (photo == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        photoMapper.deleteById(photoId);
        AlbumDO album = albumMapper.selectById(photo.getAlbumId());
        if (album != null && photo.getUrl().equals(album.getCoverUrl())) {
            List<AlbumVO.PhotoVO> rest = photos(photo.getAlbumId());
            album.setCoverUrl(rest.isEmpty() ? null : rest.get(0).url());
            albumMapper.updateById(album);
        }
    }

    private AlbumDO require(Long id) {
        AlbumDO album = albumMapper.selectById(id);
        if (album == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        return album;
    }
}
