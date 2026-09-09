package com.personalink.server.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.personalink.server.dto.PairHistoryResponse;
import com.personalink.server.exception.BusinessException;
import com.personalink.server.mapper.PairSessionMapper;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PairHistoryTest {
    @Test
    void historyScopesToAuthenticatedUserAndPaginatesWithoutPerRecordQueries() {
        var mapper = mock(PairSessionMapper.class);
        var service = new PairAssessmentServiceImpl(null, null, null, null, null, null, new ObjectMapper(), null);
        ReflectionTestUtils.setField(service, "baseMapper", mapper);
        var row = new PairHistoryResponse("21", "沟通测试", 2, 5, LocalDateTime.now(), LocalDateTime.now());
        when(mapper.countHistory("owner")).thenReturn(21L);
        when(mapper.selectHistory(eq("owner"), eq(20L), eq(20L), any(), eq(1), eq(5)))
                .thenReturn(List.of(row));
        var page = service.history("owner", 2, 20);
        assertEquals(21, page.total());
        assertEquals(2, page.page());
        assertEquals(List.of(row), page.records());
        assertTrue(service.history("owner", 3, 20).records().isEmpty());
        verify(mapper).selectHistory(eq("owner"), eq(20L), eq(20L), any(), eq(1), eq(5));
        verify(mapper, times(2)).countHistory("owner");
        verifyNoMoreInteractions(mapper);
    }

    @Test
    void invalidIdentityAndPaginationNeverReachDatabase() {
        var mapper = mock(PairSessionMapper.class);
        var service = new PairAssessmentServiceImpl(null, null, null, null, null, null, new ObjectMapper(), null);
        ReflectionTestUtils.setField(service, "baseMapper", mapper);
        assertThrows(BusinessException.class, () -> service.history(null, 1, 20));
        assertThrows(BusinessException.class, () -> service.history(" ", 1, 20));
        assertThrows(BusinessException.class, () -> service.history("owner", 0, 20));
        assertThrows(BusinessException.class, () -> service.history("owner", Long.MAX_VALUE, 20));
        assertThrows(BusinessException.class, () -> service.history("owner", 1, 101));
        verifyNoInteractions(mapper);
    }

    @Test
    void sqlUsesSameVisibleScopeAndReadOnlyExpiryProjection() throws Exception {
        var configuration = new Configuration();
        String resource = "mapper/PairSessionMapper.xml";
        try (var input = getClass().getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(input);
            new XMLMapperBuilder(input, configuration, resource, configuration.getSqlFragments()).parse();
        }
        var parameters = Map.of("openId", "owner", "offset", 20L, "size", 20L,
                "now", LocalDateTime.now(), "pendingStatus", 1, "expiredStatus", 5);
        String prefix = "com.personalink.server.mapper.PairSessionMapper.";
        String count = configuration.getMappedStatement(prefix + "countHistory")
                .getBoundSql(parameters).getSql().replaceAll("\\s+", " ").trim();
        String list = configuration.getMappedStatement(prefix + "selectHistory")
                .getBoundSql(parameters).getSql().replaceAll("\\s+", " ").trim();
        String scope = count.substring(count.indexOf("WHERE ") + 6);
        assertTrue(list.contains("WHERE " + scope + " ORDER BY"));
        assertTrue(scope.contains("p.deleted = 0"));
        assertTrue(scope.contains("p.initiator_open_id = ? AND p.initiator_visible_flag = 1"));
        assertTrue(scope.contains("p.partner_open_id = ? AND p.partner_visible_flag = 1"));
        assertTrue(list.contains("ORDER BY p.create_date DESC, p.id DESC LIMIT ? OFFSET ?"));
        assertTrue(list.contains("CASE WHEN p.pair_status = ? AND p.expires_at < ? THEN ? ELSE p.pair_status END"));
        assertTrue(list.contains("LEFT JOIN t_test_version v ON v.id = p.version_id AND v.deleted = 0"));
        assertFalse(list.contains("UPDATE"));
        String projection = list.substring(0, list.indexOf("FROM"));
        assertFalse(projection.contains("open_id"));
        assertFalse(projection.contains("invite"));
        assertFalse(projection.contains("answer"));
    }
}
