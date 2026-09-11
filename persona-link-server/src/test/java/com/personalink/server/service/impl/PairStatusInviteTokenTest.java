package com.personalink.server.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.personalink.server.dto.PairSessionResponse;
import com.personalink.server.entity.PairSessionEntity;
import com.personalink.server.mapper.PairReportMapper;
import com.personalink.server.mapper.PairSessionMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class PairStatusInviteTokenTest {
    private final PairSessionMapper pairs = mock(PairSessionMapper.class);
    private final PairReportMapper pairReports = mock(PairReportMapper.class);
    private PairAssessmentServiceImpl service;

    @BeforeEach
    void setup() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), "pair-status-invite-token-test"),
                PairSessionEntity.class);
        this.service = new PairAssessmentServiceImpl(null, null, null, null,
                this.pairReports, null, new ObjectMapper(), null);
        ReflectionTestUtils.setField(this.service, "baseMapper", this.pairs);
        ReflectionTestUtils.setField(this.service, "entityClass", PairSessionEntity.class);
        when(this.pairReports.selectOne(any(), eq(false))).thenReturn(null);
    }

    private PairSessionEntity pair(int status) {
        PairSessionEntity pair = new PairSessionEntity();
        pair.setId(60L);
        pair.setCreateRequestId("pair-create-request");
        pair.setInitiatorOpenId("owner");
        pair.setInitiatorAnswerSessionId(10L);
        pair.setPartnerOpenId("guest");
        pair.setPartnerAnswerSessionId(11L);
        pair.setInitiatorVisibleFlag(1);
        pair.setPartnerVisibleFlag(1);
        pair.setPairStatus(status);
        pair.setExpiresAt(LocalDateTime.now().plusHours(1));
        return pair;
    }

    @Test
    void initiatorCanReReadSameInviteTokenWhileWaiting() {
        when(this.pairs.selectById(60L)).thenReturn(this.pair(1));
        PairSessionResponse first = this.service.getStatus("owner", 60L);
        assertNotNull(first.inviteToken());
        assertTrue(first.inviteToken().matches("[A-Z2-9]{5}"));
        PairSessionResponse second = this.service.getStatus("owner", 60L);
        assertEquals(first.inviteToken(), second.inviteToken(), "重新分享必须复用同一邀请码");
    }

    @Test
    void joinedOrPartnerRequestsNeverExposeInviteToken() {
        when(this.pairs.selectById(60L)).thenReturn(this.pair(2));
        assertNull(this.service.getStatus("owner", 60L).inviteToken());
        PairSessionResponse partner = this.service.getStatus("guest", 60L);
        assertEquals("PARTNER", partner.myRole());
        assertNull(partner.inviteToken());
    }

    @Test
    void expiredWaitingPairNeverExposesInviteToken() {
        PairSessionEntity expired = this.pair(1);
        expired.setExpiresAt(LocalDateTime.now().minusHours(1));
        when(this.pairs.selectById(60L)).thenReturn(expired);
        PairSessionResponse response = this.service.getStatus("owner", 60L);
        assertEquals(5, response.pairStatus());
        assertNull(response.inviteToken());
    }
}
