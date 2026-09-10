package com.personalink.server.mapper;

import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import com.personalink.server.service.impl.AppConfigServiceImpl;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.AutoConfigurationPackages;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;

class AppConfigMapperContextTest {

    @Test
    void autoConfigurationRegistersMapperAndInjectsService() {
        // 仅验证真实扫描和装配；未执行 SQL，不连接业务数据库。
        new ApplicationContextRunner()
                .withInitializer(context -> AutoConfigurationPackages.register(
                        (BeanDefinitionRegistry) context.getBeanFactory(), "com.personalink.server"))
                .withConfiguration(AutoConfigurations.of(MybatisPlusAutoConfiguration.class))
                .withBean(DataSource.class, () -> new DriverManagerDataSource(
                        "jdbc:mysql://127.0.0.1:1/mapper_registration_test", "", ""))
                .withBean(AppConfigServiceImpl.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(AppConfigMapper.class);
                    assertThat(context.getBean(AppConfigServiceImpl.class).getBaseMapper())
                            .isSameAs(context.getBean(AppConfigMapper.class));
                    assertThat(context.getBean(SqlSessionFactory.class).getConfiguration()
                            .hasStatement(AppConfigMapper.class.getName() + ".upsert")).isTrue();
                });
    }
}
