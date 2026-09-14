package com.personalink.server.mapper;

import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import com.personalink.server.entity.FeedbackEntity;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.AutoConfigurationPackages;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;

class FeedbackMapperContextTest {

    @Test
    void insertIdempotentPersistsAnonymousIdentityAndNickname() {
        // 仅验证映射装配和插入列，未执行 SQL，不连接业务数据库。
        new ApplicationContextRunner()
                .withInitializer(context -> AutoConfigurationPackages.register(
                        (BeanDefinitionRegistry) context.getBeanFactory(), "com.personalink.server"))
                .withConfiguration(AutoConfigurations.of(MybatisPlusAutoConfiguration.class))
                .withBean(DataSource.class, () -> new DriverManagerDataSource(
                        "jdbc:mysql://127.0.0.1:1/feedback_mapper_test", "", ""))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    FeedbackEntity entity = new FeedbackEntity();
                    entity.setOpenId("");
                    entity.setNickname("小明");
                    entity.setRequestId("key");
                    entity.setContent("反馈内容");
                    BoundSql boundSql = context.getBean(SqlSessionFactory.class).getConfiguration()
                            .getMappedStatement(FeedbackMapper.class.getName() + ".insertIdempotent")
                            .getBoundSql(entity);
                    assertThat(boundSql.getSql())
                            .contains("open_id", "nickname", "request_id", "content");
                    assertThat(boundSql.getParameterMappings())
                            .extracting(mapping -> mapping.getProperty())
                            .contains("openId", "nickname", "requestId", "content");
                });
    }
}
