package com.personalink.server.bootstrap;

import com.personalink.server.entity.AdminAccountEntity;
import com.personalink.server.service.AdminAccountService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

/**
 * 使用部署环境变量安全初始化首个后台管理员。
 */
@Component
public class AdminAccountBootstrap implements ApplicationRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(AdminAccountBootstrap.class);
    private static final int ADMIN_ROLE = 1;
    private static final int ENABLED = 1;
    private static final int NOT_DELETED = 0;

    private final AdminAccountService adminAccountService;
    private final String username;
    private final String password;
    private final String displayName;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public AdminAccountBootstrap(AdminAccountService adminAccountService,
                                 @Value("${PERSONA_LINK_ADMIN_USERNAME:}") String username,
                                 @Value("${PERSONA_LINK_ADMIN_PASSWORD:}") String password,
                                 @Value("${PERSONA_LINK_ADMIN_DISPLAY_NAME:管理员}") String displayName) {
        this.adminAccountService = adminAccountService;
        this.username = username;
        this.password = password;
        this.displayName = displayName;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void run(ApplicationArguments args) {
        boolean usernameBlank = this.username.isBlank();
        boolean passwordBlank = this.password.isBlank();
        if (usernameBlank && passwordBlank) {
            return;
        }
        if (usernameBlank || passwordBlank) {
            throw new IllegalStateException("初始化管理员必须同时配置账号和密码");
        }
        if (Objects.nonNull(this.adminAccountService.findByUsername(this.username))) {
            return;
        }

        AdminAccountEntity account = new AdminAccountEntity();
        account.setUsername(this.username);
        account.setPasswordHash(this.passwordEncoder.encode(this.password));
        account.setDisplayName(this.displayName.isBlank() ? "管理员" : this.displayName);
        account.setRoleType(ADMIN_ROLE);
        account.setStatus(ENABLED);
        account.setDeleted(NOT_DELETED);
        if (!this.adminAccountService.save(account)) {
            throw new IllegalStateException("初始化管理员失败");
        }
        LOGGER.info("[后台账号] 已初始化管理员账号：{}", this.username);
    }
}
