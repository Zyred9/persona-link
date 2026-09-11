package com.personalink.server.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.personalink.server.dto.AiGeneratedDimension;
import com.personalink.server.dto.AiGeneratedOption;
import com.personalink.server.dto.AiGeneratedQuestion;
import com.personalink.server.dto.AiGeneratedQuestionBatch;
import com.personalink.server.dto.AiGeneratedResultRule;
import com.personalink.server.dto.AiGeneratedSetup;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AiGenerationValidatorTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void validateSetupShouldRequireContinuousCoverage() {
        AiGeneratedSetup valid = new AiGeneratedSetup(
                List.of(new AiGeneratedDimension("D1", "外向", 0)),
                List.of(this.rule("LOW", 0, 50), this.rule("HIGH", 50, 100)));
        AiGeneratedSetup gap = new AiGeneratedSetup(
                valid.dimensions(),
                List.of(this.rule("LOW", 0, 40), this.rule("HIGH", 50, 100)));

        assertDoesNotThrow(() -> AiGenerationValidator.validateSetup(valid));
        assertThrows(IllegalArgumentException.class, () -> AiGenerationValidator.validateSetup(gap));
    }

    @Test
    void validateQuestionBatchShouldRejectUnknownDimension() {
        AiGeneratedQuestion validQuestion = new AiGeneratedQuestion(
                1, "D1", 1, 1, 1, "你更喜欢怎样的周末？", 1, 1,
                List.of(new AiGeneratedOption("A", "独处", null, 1, 2),
                        new AiGeneratedOption("B", "聚会", null, 5, 1)));
        AiGeneratedQuestion invalidQuestion = new AiGeneratedQuestion(
                1, "UNKNOWN", 1, 1, 1, "你更喜欢怎样的周末？", 1, 1,
                validQuestion.options());

        assertDoesNotThrow(() -> AiGenerationValidator.validateQuestionBatch(
                new AiGeneratedQuestionBatch(List.of(validQuestion)), Set.of("D1"), 1, 1, List.of()));
        assertThrows(IllegalArgumentException.class, () -> AiGenerationValidator.validateQuestionBatch(
                new AiGeneratedQuestionBatch(List.of(invalidQuestion)), Set.of("D1"), 1, 1, List.of()));
    }

    @Test
    void validateQuestionBatchShouldRequireMultipleChoiceSelectAll() {
        AiGeneratedQuestion validQuestion = new AiGeneratedQuestion(
                2, null, 2, 2, 1, "以下哪些描述符合你？", 1, 1,
                List.of(new AiGeneratedOption("A", "描述一", "D1", 1, 2),
                        new AiGeneratedOption("B", "描述二", "D1", 1, 1)));
        AiGeneratedQuestion partialRange = new AiGeneratedQuestion(
                2, null, 1, 2, 1, "以下哪些描述符合你？", 1, 1, validQuestion.options());

        assertDoesNotThrow(() -> AiGenerationValidator.validateQuestionBatch(
                new AiGeneratedQuestionBatch(List.of(validQuestion)), Set.of("D1"), 1, 1, List.of()));
        assertThrows(IllegalArgumentException.class, () -> AiGenerationValidator.validateQuestionBatch(
                new AiGeneratedQuestionBatch(List.of(partialRange)), Set.of("D1"), 1, 1, List.of()));
    }

    @Test
    void validateSetupShouldAcceptSafeLowercaseAndChineseCodes() {
        AiGeneratedSetup setup = new AiGeneratedSetup(
                List.of(new AiGeneratedDimension("社交_energy", "社交能量", 0)),
                List.of(this.rule("社交_energy", "low-state", 0, 50),
                        this.rule("社交_energy", "high_state", 50, 100)));

        assertDoesNotThrow(() -> AiGenerationValidator.validateSetup(setup));
    }

    @Test
    void validateSetupShouldStillRejectUnsafeCodeCharacters() {
        AiGeneratedSetup setup = new AiGeneratedSetup(
                List.of(new AiGeneratedDimension("social/energy", "社交能量", 0)),
                List.of(this.rule("social/energy", "LOW", 0, 100)));

        assertThrows(IllegalArgumentException.class, () -> AiGenerationValidator.validateSetup(setup));
    }

    @Test
    void validateSetupShouldRejectAccentedAndFullWidthCodes() {
        AiGeneratedSetup accented = new AiGeneratedSetup(
                List.of(new AiGeneratedDimension("Café", "社交能量", 0)),
                List.of(this.rule("Café", "LOW", 0, 100)));
        AiGeneratedSetup fullWidth = new AiGeneratedSetup(
                List.of(new AiGeneratedDimension("Ａ1", "社交能量", 0)),
                List.of(this.rule("Ａ1", "LOW", 0, 100)));

        assertThrows(IllegalArgumentException.class, () -> AiGenerationValidator.validateSetup(accented));
        assertThrows(IllegalArgumentException.class, () -> AiGenerationValidator.validateSetup(fullWidth));
    }

    @Test
    void validateSetupShouldNormalizeAdjacentIntegerClosedRanges() {
        AiGeneratedSetup setup = new AiGeneratedSetup(
                List.of(new AiGeneratedDimension("D1", "外向", 0)),
                List.of(this.rule("LOW", 0, 33), this.rule("MID", 34, 66),
                        this.rule("HIGH", 67, 100)));

        AiGeneratedSetup normalized = AiGenerationValidator.validateSetup(setup);

        assertEquals(new BigDecimal("34"), normalized.resultRules().get(0).scoreMax());
        assertEquals(new BigDecimal("67"), normalized.resultRules().get(1).scoreMax());
        assertEquals(new BigDecimal("100"), normalized.resultRules().get(2).scoreMax());
    }

    @Test
    void validateSetupShouldStillRejectRealRangeGap() {
        AiGeneratedSetup setup = new AiGeneratedSetup(
                List.of(new AiGeneratedDimension("D1", "外向", 0)),
                List.of(this.rule("LOW", 0, 33), this.rule("MID", 35, 66),
                        this.rule("HIGH", 67, 100)));

        assertThrows(IllegalArgumentException.class, () -> AiGenerationValidator.validateSetup(setup));
    }

    private AiGeneratedResultRule rule(String resultCode, int scoreMin, int scoreMax) {
        return this.rule("D1", resultCode, scoreMin, scoreMax);
    }

    private AiGeneratedResultRule rule(String dimensionCode, String resultCode, int scoreMin, int scoreMax) {
        return new AiGeneratedResultRule(dimensionCode, resultCode, resultCode,
                BigDecimal.valueOf(scoreMin), BigDecimal.valueOf(scoreMax),
                this.objectMapper.createObjectNode().put("text", resultCode), null, null, scoreMin);
    }
}
