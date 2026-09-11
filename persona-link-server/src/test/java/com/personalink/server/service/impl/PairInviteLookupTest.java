package com.personalink.server.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.personalink.server.dto.PairInviteResponse;
import com.personalink.server.entity.PairSessionEntity;
import com.personalink.server.exception.BusinessException;
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

class PairInviteLookupTest {
    private final PairSessionMapper pairs = mock(PairSessionMapper.class);
    private PairAssessmentServiceImpl service;

    @BeforeEach
    void setup() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), "pair-invite-lookup-test"),
                PairSessionEntity.class);
        this.service = new PairAssessmentServiceImpl(null, null, null, null,
                null, null, new ObjectMapper(), null);
        ReflectionTestUtils.setField(this.service, "baseMapper", this.pairs);
        ReflectionTestUtils.setField(this.service, "entityClass", PairSessionEntity.class);
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

    private void stubLookup(PairSessionEntity pair) {
        when(this.pairs.selectOne(any())).thenReturn(pair);
        when(this.pairs.selectOne(any(), eq(false))).thenReturn(pair);
    }

    @Test
    void participantGetsRoutingContext() {
        this.stubLookup(this.pair(1));
        PairInviteResponse initiator = this.service.invite("owner", "abc12");
        assertEquals("60", initiator.pairSessionId());
        assertEquals("INITIATOR", initiator.myRole());
        assertEquals("10", initiator.answerSessionId());
        assertFalse(initiator.joinable());

        this.stubLookup(this.pair(2));
        PairInviteResponse partner = this.service.invite("guest", "abc12");
        assertEquals("PARTNER", partner.myRole());
        assertEquals("11", partner.answerSessionId());
        assertFalse(partner.joinable());
    }

    @Test
    void outsiderOnlySeesJoinableFlag() {
        this.stubLookup(this.pair(1));
        PairInviteResponse joinable = this.service.invite("stranger", "abc12");
        assertNull(joinable.pairSessionId());
        assertNull(joinable.myRole());
        assertNull(joinable.answerSessionId());
        assertTrue(joinable.joinable());

        this.stubLookup(this.pair(2));
        PairInviteResponse taken = this.service.invite("stranger", "abc12");
        assertEquals(2, taken.pairStatus());
        assertNull(taken.pairSessionId());
        assertFalse(taken.joinable());
    }

    @Test
    void expiredAndUnknownInvitesAreRejected() {
        PairSessionEntity expired = this.pair(1);
        expired.setExpiresAt(LocalDateTime.now().minusHours(1));
        this.stubLookup(expired);
        PairInviteResponse response = this.service.invite("stranger", "abc12");
        assertEquals(5, response.pairStatus());
        assertFalse(response.joinable());

        this.stubLookup(null);
        assertThrows(BusinessException.class, () -> this.service.invite("stranger", "abc12"));
    }
}
