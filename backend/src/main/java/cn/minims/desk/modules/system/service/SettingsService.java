package cn.minims.desk.modules.system.service;

import cn.minims.desk.common.api.ErrorCode;
import cn.minims.desk.common.exception.BizException;
import cn.minims.desk.framework.crypto.AesGcmCipher;
import cn.minims.desk.modules.integration.entity.IntegrationConfigDO;
import cn.minims.desk.modules.integration.mapper.IntegrationConfigMapper;
import cn.minims.desk.modules.integration.weather.service.WeatherService;
import cn.minims.desk.modules.integration.weather.vo.WeatherVO;
import cn.minims.desk.modules.system.vo.SettingsVO;
import cn.minims.desk.modules.user.entity.SysUserSettingDO;
import cn.minims.desk.modules.user.mapper.SysUserSettingMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 个人设置与第三方集成配置。
 */
@Service
public class SettingsService {

    private final SysUserSettingMapper settingMapper;
    private final IntegrationConfigMapper integrationMapper;
    private final AesGcmCipher cipher;
    private final WeatherService weatherService;

    public SettingsService(SysUserSettingMapper settingMapper,
                           IntegrationConfigMapper integrationMapper,
                           AesGcmCipher cipher,
                           WeatherService weatherService) {
        this.settingMapper = settingMapper;
        this.integrationMapper = integrationMapper;
        this.cipher = cipher;
        this.weatherService = weatherService;
    }

    public SettingsVO get() {
        SysUserSettingDO setting = requireSetting();
        return new SettingsVO(
                setting.getCity(),
                setting.getTheme(),
                setting.getWeekStart(),
                setting.getSidebarCollapsed() != null && setting.getSidebarCollapsed() == 1);
    }

    @Transactional(rollbackFor = Exception.class)
    public SettingsVO update(String city, String theme) {
        SysUserSettingDO setting = requireSetting();
        if (StringUtils.hasText(city)) {
            setting.setCity(city.trim());
        }
        if (StringUtils.hasText(theme)) {
            setting.setTheme(theme.trim());
        }
        setting.setUpdatedAt(LocalDateTime.now());
        settingMapper.updateById(setting);
        return get();
    }

    public SettingsVO.IntegrationVO integration(String provider) {
        IntegrationConfigDO config = findIntegration(provider);
        if (config == null) {
            return new SettingsVO.IntegrationVO(provider, "", false, false, "");
        }
        String plain = cipher.decrypt(config.getApiKeyCipher());
        return new SettingsVO.IntegrationVO(
                provider,
                config.getApiHost(),
                config.getEnabled() != null && config.getEnabled() == 1,
                plain != null && !plain.isBlank(),
                cipher.mask(plain));
    }

    @Transactional(rollbackFor = Exception.class)
    public SettingsVO.IntegrationVO saveIntegration(String provider, String apiKey, String apiHost, Boolean enabled) {
        IntegrationConfigDO config = findIntegration(provider);
        LocalDateTime now = LocalDateTime.now();
        if (config == null) {
            config = new IntegrationConfigDO();
            config.setProvider(provider);
            config.setCreatedAt(now);
        }

        // 传回掩码视为「不修改 Key」，避免前端回存 ****xxxx
        if (StringUtils.hasText(apiKey) && !apiKey.contains("****")) {
            config.setApiKeyCipher(cipher.encrypt(apiKey.trim()));
        }
        if (StringUtils.hasText(apiHost)) {
            config.setApiHost(apiHost.trim());
        }
        if (enabled != null) {
            config.setEnabled(enabled ? 1 : 0);
        }
        config.setUpdatedAt(now);

        if (config.getId() == null) {
            integrationMapper.insert(config);
        } else {
            integrationMapper.updateById(config);
        }
        return integration(provider);
    }

    public SettingsVO.TestResult testWeather(String city) {
        WeatherVO weather = weatherService.refresh(city);
        if (weather.isMock()) {
            return new SettingsVO.TestResult(false, "未配置有效 Key 或请求失败，当前为模拟数据", weather.temp(), weather.text());
        }
        return new SettingsVO.TestResult(true, "连接成功", weather.temp(), weather.text());
    }

    private SysUserSettingDO requireSetting() {
        SysUserSettingDO setting = settingMapper.selectOne(
                Wrappers.lambdaQuery(SysUserSettingDO.class).last("limit 1"));
        if (setting != null) {
            return setting;
        }
        // 老账号可能没有设置行，惰性补一条
        setting = new SysUserSettingDO();
        setting.setTheme("auto");
        setting.setCity("北京");
        setting.setWeekStart(1);
        setting.setSidebarCollapsed(0);
        setting.setCreatedAt(LocalDateTime.now());
        setting.setUpdatedAt(LocalDateTime.now());
        settingMapper.insert(setting);
        return setting;
    }

    private IntegrationConfigDO findIntegration(String provider) {
        if (!StringUtils.hasText(provider)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "缺少 provider");
        }
        return integrationMapper.selectOne(Wrappers.lambdaQuery(IntegrationConfigDO.class)
                .eq(IntegrationConfigDO::getProvider, provider.trim())
                .last("limit 1"));
    }
}
