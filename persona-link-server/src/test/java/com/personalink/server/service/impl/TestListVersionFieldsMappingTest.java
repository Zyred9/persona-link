package com.personalink.server.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.personalink.server.dto.TestQuery;
import com.personalink.server.dto.TestResponse;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestListVersionFieldsMappingTest {

    @Test
    void listAndDetailShouldSelectDisplayFieldsFromOneVersionAndSerializeThem() throws Exception {
        Configuration configuration = new Configuration();
        String resource = "mapper/TestMapper.xml";
        try (InputStream input = this.getClass().getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(input);
            new XMLMapperBuilder(input, configuration, resource, configuration.getSqlFragments()).parse();
        }
        String namespace = "com.personalink.server.mapper.TestMapper.";
        Map<String, Object> parameters = Map.of("query", new TestQuery(), "offset", 0L, "size", 10L, "id", 1L);
        for (String statement : new String[]{"selectTestPage", "selectTestDetail"}) {
            String sql = configuration.getMappedStatement(namespace + statement)
                    .getBoundSql(parameters).getSql().replaceAll("\\s+", " ");
            assertTrue(sql.contains("display_version.cover_url, display_version.draw_question_count, display_version.estimated_minutes"));
            assertTrue(sql.contains("LEFT JOIN t_test_version display_version ON display_version.id = ( SELECT v.id"));
            assertTrue(sql.contains("WHERE v.test_id = t.id AND v.deleted = 0 ORDER BY CASE WHEN v.version_status = 1 THEN 0 WHEN v.version_status = 4 THEN 1 ELSE 2 END, v.version_no DESC LIMIT 1)"));
            assertTrue(sql.contains("display_version.version_no AS current_version_no"));
            assertTrue(sql.contains("display_version.version_status AS current_version_status"));
            assertFalse(sql.contains("SELECT v.version_no"));
            assertEquals(sql.indexOf("LEFT JOIN t_test_version"), sql.lastIndexOf("LEFT JOIN t_test_version"));
        }
        String countSql = configuration.getMappedStatement(namespace + "countTestPage").getBoundSql(parameters).getSql();
        assertFalse(countSql.contains("display_version"));

        TestResponse response = new TestResponse();
        response.setCoverUrl("/uploads/cover.png");
        response.setDrawQuestionCount(5);
        response.setEstimatedMinutes(3);
        var json = new ObjectMapper().valueToTree(response);
        assertEquals("/uploads/cover.png", json.get("coverUrl").asText());
        assertEquals(5, json.get("drawQuestionCount").asInt());
        assertEquals(3, json.get("estimatedMinutes").asInt());
    }
}
