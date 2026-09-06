package com.personalink.server.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.personalink.server.dto.MiniappTestDetailResponse;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MiniappDetailImageMappingTest {

    @Test
    void publishedDetailColumnsShouldMatchRecordConstructorOrderWithoutChangingHome() throws Exception {
        Configuration configuration = new Configuration();
        String resource = "mapper/MiniappContentMapper.xml";
        try (InputStream input = this.getClass().getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(input);
            new XMLMapperBuilder(input, configuration, resource, configuration.getSqlFragments()).parse();
        }
        String namespace = "com.personalink.server.mapper.MiniappContentMapper.";
        String sql = configuration.getMappedStatement(namespace + "selectPublishedTestDetail")
                .getBoundSql(Map.of("testId", 12L)).getSql();
        String projection = sql.substring(sql.indexOf("SELECT") + 6, sql.indexOf("FROM"));
        List<String> columns = Arrays.stream(projection.split(","))
                .map(String::trim)
                .map(column -> column.substring(Math.max(column.lastIndexOf(' '), column.lastIndexOf('.')) + 1))
                .toList();
        List<String> fields = Arrays.stream(MiniappTestDetailResponse.class.getRecordComponents())
                .map(field -> field.getName().replaceAll("([A-Z])", "_$1").toLowerCase())
                .toList();
        assertEquals(fields, columns);
        assertTrue(sql.contains("current_tv.version_status = 4"));
        String homeSql = configuration.getMappedStatement(namespace + "selectHomeTests")
                .getBoundSql(Map.of("homeDisplay", 0)).getSql();
        assertFalse(homeSql.contains("detail_image_url"));
        assertTrue(homeSql.contains("tv.cover_url"));
        assertFalse(homeSql.contains("icon_url"));
        assertFalse(sql.contains("icon_url"));

        var detail = new MiniappTestDetailResponse("12", "9", 1, "2", "日常", "测试",
                "/uploads/cover.png", "/uploads/detail.png", 5, 3, "说明");
        var json = new ObjectMapper().valueToTree(detail);
        assertEquals("/uploads/cover.png", json.get("coverUrl").asText());
        assertEquals("/uploads/detail.png", json.get("detailImageUrl").asText());
        assertFalse(json.has("iconUrl"));
    }
}
