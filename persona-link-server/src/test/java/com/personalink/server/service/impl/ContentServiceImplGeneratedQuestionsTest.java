package com.personalink.server.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.personalink.server.dto.QuestionSaveRequest;
import com.personalink.server.entity.QuestionEntity;
import com.personalink.server.entity.TestVersionEntity;
import com.personalink.server.exception.AiQuestionTextConflictException;
import com.personalink.server.exception.BusinessException;
import com.personalink.server.mapper.QuestionMapper;
import com.personalink.server.mapper.TestVersionMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ContentServiceImplGeneratedQuestionsTest {

    @Test
    void appendShouldDistinguishQuestionNumberAndSqlTextConflictsBeforeWriting() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "test"),
                TestVersionEntity.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "test"),
                QuestionEntity.class);
        TestVersionMapper versions = mock(TestVersionMapper.class);
        QuestionMapper questions = mock(QuestionMapper.class);
        TestVersionEntity draft = new TestVersionEntity();
        draft.setId(7L);
        draft.setVersionStatus(1);
        draft.setDeleted(0);
        when(versions.selectOne(any(), eq(false))).thenReturn(draft);
        ContentServiceImpl service = new ContentServiceImpl(versions, null, questions, null, null,
                null, null, new ObjectMapper());
        List<QuestionSaveRequest> requests = List.of(new QuestionSaveRequest(1, 10L, 1, 1, 21,
                "Café", 1, 21, List.of()));

        when(questions.selectCount(any())).thenReturn(1L);
        BusinessException numberConflict = assertThrows(BusinessException.class,
                () -> service.appendGeneratedQuestions(7L, requests, 1L));
        assertFalse(numberConflict instanceof AiQuestionTextConflictException);
        assertTrue(numberConflict.getMessage().contains("题号已存在"));

        when(questions.selectCount(any())).thenReturn(0L);
        when(questions.selectConflictingGeneratedQuestionNos(7L, requests)).thenReturn(List.of(21));
        assertEquals(0, service.appendGeneratedQuestions(7L, requests, 1L));
        verify(questions, never()).insertBatch(anyList());
    }
}
