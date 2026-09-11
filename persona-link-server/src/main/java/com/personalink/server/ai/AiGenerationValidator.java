package com.personalink.server.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.personalink.server.dto.AiGeneratedDimension;
import com.personalink.server.dto.AiGeneratedOption;
import com.personalink.server.dto.AiGeneratedQuestion;
import com.personalink.server.dto.AiGeneratedQuestionBatch;
import com.personalink.server.dto.AiGeneratedResultRule;
import com.personalink.server.dto.AiGeneratedSetup;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** AI 生成结构的严格校验工具。 */
public final class AiGenerationValidator {

    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final int SINGLE = 1;
    private static final int MULTIPLE = 2;
    private static final Pattern DIMENSION_CODE_PATTERN = Pattern.compile("[A-Za-z0-9_\\p{IsHan}-]{1,32}");
    private static final Pattern RESULT_CODE_PATTERN = Pattern.compile("[A-Za-z0-9_\\p{IsHan}-]{1,32}");
    private static final Pattern OPTION_CODE_PATTERN = Pattern.compile("[A-Za-z0-9_\\p{IsHan}-]{1,16}");

    private AiGenerationValidator() {
    }

    public static AiGeneratedSetup validateSetup(AiGeneratedSetup setup) {
        if (Objects.isNull(setup) || Objects.isNull(setup.dimensions())
                || setup.dimensions().isEmpty() || setup.dimensions().size() > 8) {
            throw invalid("计分维度数量必须为 1-8 个");
        }
        Set<String> dimensionCodes = new HashSet<>();
        Set<String> normalizedDimensionCodes = new HashSet<>();
        for (AiGeneratedDimension dimension : setup.dimensions()) {
            if (Objects.isNull(dimension)) {
                throw invalid("计分维度不能为空");
            }
            requireCode(dimension.dimensionCode(), DIMENSION_CODE_PATTERN, "维度编码");
            requireText(dimension.dimensionName(), 64, "维度名称");
            dimensionCodes.add(dimension.dimensionCode());
            if (!normalizedDimensionCodes.add(normalizedCode(dimension.dimensionCode()))
                    || invalidSort(dimension.sortNo())) {
                throw invalid("维度编码重复或排序值无效");
            }
        }
        if (Objects.isNull(setup.resultRules()) || setup.resultRules().isEmpty()) {
            throw invalid("结果规则不能为空");
        }
        Set<String> resultCodes = new HashSet<>();
        for (AiGeneratedResultRule rule : setup.resultRules()) {
            if (Objects.isNull(rule)) {
                throw invalid("结果规则不能为空");
            }
            if (!dimensionCodes.contains(rule.dimensionCode())) {
                throw invalid("结果规则引用了不存在的维度");
            }
            requireCode(rule.resultCode(), RESULT_CODE_PATTERN, "结果编码");
            requireText(rule.resultName(), 100, "结果名称");
            requireObject(rule.basicResultJson(), "基础结果文案");
            requireOptionalObject(rule.deepResultJson(), "深度结果文案");
            requireOptionalObject(rule.shareCopyJson(), "分享文案");
            if (!resultCodes.add(normalizedCode(rule.resultCode())) || invalidSort(rule.sortNo())
                    || Objects.isNull(rule.scoreMin()) || Objects.isNull(rule.scoreMax())
                    || rule.scoreMin().compareTo(ZERO) < 0 || rule.scoreMax().compareTo(HUNDRED) > 0
                    || rule.scoreMin().compareTo(rule.scoreMax()) >= 0) {
                throw invalid("结果规则编码、排序或分数区间无效");
            }
        }
        setup = new AiGeneratedSetup(setup.dimensions(), normalizeClosedIntegerRanges(setup.resultRules()));
        Map<String, List<AiGeneratedResultRule>> ruleMap = setup.resultRules().stream()
                .collect(Collectors.groupingBy(AiGeneratedResultRule::dimensionCode));
        if (!ruleMap.keySet().equals(dimensionCodes)) {
            throw invalid("每个维度都必须配置结果规则");
        }
        ruleMap.forEach(AiGenerationValidator::validateCoverage);
        return setup;
    }

    public static void validateQuestionBatch(AiGeneratedQuestionBatch batch,
                                             Set<String> dimensionCodes,
                                             int firstQuestionNo,
                                             int expectedCount,
                                             List<String> existingQuestionTexts) {
        if (Objects.isNull(batch) || Objects.isNull(batch.questions())
                || batch.questions().size() != expectedCount) {
            throw invalid("本批题目数量与要求不一致");
        }
        if (batch.questions().stream().anyMatch(question -> Objects.isNull(question)
                || Objects.isNull(question.questionNo()))) {
            throw invalid("题目和题号不能为空");
        }
        Map<Integer, AiGeneratedQuestion> questionMap = batch.questions().stream()
                .collect(Collectors.toMap(AiGeneratedQuestion::questionNo, Function.identity(), (left, right) -> {
                    throw invalid("本批题号重复");
                }));
        Set<String> questionTexts = existingQuestionTexts.stream()
                .map(text -> text.trim().toLowerCase(Locale.ROOT)).collect(Collectors.toSet());
        for (int index = 0; index < expectedCount; index++) {
            int questionNo = firstQuestionNo + index;
            AiGeneratedQuestion question = questionMap.get(questionNo);
            if (Objects.isNull(question)) {
                throw invalid("本批题号必须连续");
            }
            validateQuestion(question, dimensionCodes);
            if (!questionTexts.add(question.questionText().trim().toLowerCase(Locale.ROOT))) {
                throw invalid("第 " + questionNo + " 题题干与已生成题目或本批题目重复："
                        + question.questionText() + "，请更换题干，不要仅调整大小写或空格");
            }
        }
    }

    private static void validateQuestion(AiGeneratedQuestion question, Set<String> dimensionCodes) {
        requireText(question.questionText(), 500, "题干");
        if (Objects.isNull(question.options()) || question.options().size() < 2 || question.options().size() > 8) {
            throw invalid("每题必须包含 2-8 个选项");
        }
        if (!Integer.valueOf(0).equals(question.requiredFlag())
                && !Integer.valueOf(1).equals(question.requiredFlag())) {
            throw invalid("是否必答标记无效");
        }
        if (invalidSort(question.sortNo())) {
            throw invalid("题目排序值无效");
        }
        if (Integer.valueOf(SINGLE).equals(question.questionType())) {
            if (!dimensionCodes.contains(question.dimensionCode())
                    || !Integer.valueOf(1).equals(question.minSelectCount())
                    || !Integer.valueOf(1).equals(question.maxSelectCount())) {
                throw invalid("单选题维度或选择数量无效");
            }
        } else if (Integer.valueOf(MULTIPLE).equals(question.questionType())) {
            if (hasText(question.dimensionCode()) || !Integer.valueOf(2).equals(question.minSelectCount())
                    || !Integer.valueOf(question.options().size()).equals(question.maxSelectCount())) {
                throw invalid("多选题必须最少选择 2 项且最多全选");
            }
        } else {
            throw invalid("题目类型无效");
        }
        Set<String> optionCodes = new HashSet<>();
        for (AiGeneratedOption option : question.options()) {
            if (Objects.isNull(option)) {
                throw invalid("题目选项不能为空");
            }
            requireCode(option.optionCode(), OPTION_CODE_PATTERN, "选项编码");
            requireText(option.optionText(), 300, "选项文案");
            if (!optionCodes.add(normalizedCode(option.optionCode())) || Objects.isNull(option.scoreValue())
                    || option.scoreValue() < Short.MIN_VALUE || option.scoreValue() > Short.MAX_VALUE
                    || invalidSort(option.sortNo())) {
                throw invalid("选项编码、分值或排序值无效");
            }
            if (Integer.valueOf(SINGLE).equals(question.questionType()) && hasText(option.dimensionCode())) {
                throw invalid("单选题选项不能绑定维度");
            }
            if (Integer.valueOf(MULTIPLE).equals(question.questionType())
                    && !dimensionCodes.contains(option.dimensionCode())) {
                throw invalid("多选题选项引用了不存在的维度");
            }
        }
    }

    private static void validateCoverage(String dimensionCode, List<AiGeneratedResultRule> source) {
        List<AiGeneratedResultRule> rules = new ArrayList<>(source);
        rules.sort(Comparator.comparing(AiGeneratedResultRule::scoreMin));
        if (rules.get(0).scoreMin().compareTo(ZERO) != 0
                || rules.get(rules.size() - 1).scoreMax().compareTo(HUNDRED) != 0) {
            throw invalid("维度“" + dimensionCode + "”的结果区间未覆盖 0-100");
        }
        for (int index = 1; index < rules.size(); index++) {
            if (rules.get(index).scoreMin().compareTo(rules.get(index - 1).scoreMax()) != 0) {
                throw invalid("维度“" + dimensionCode + "”的结果区间存在断档或重叠");
            }
        }
    }

    private static List<AiGeneratedResultRule> normalizeClosedIntegerRanges(
            List<AiGeneratedResultRule> source) {
        Map<String, BigDecimal> adjustedMaxMap = source.stream()
                .collect(Collectors.groupingBy(AiGeneratedResultRule::dimensionCode))
                .values().stream()
                .flatMap(rules -> {
                    List<AiGeneratedResultRule> sorted = new ArrayList<>(rules);
                    sorted.sort(Comparator.comparing(AiGeneratedResultRule::scoreMin));
                    Map<String, BigDecimal> adjusted = new java.util.HashMap<>();
                    for (int index = 1; index < sorted.size(); index++) {
                        AiGeneratedResultRule previous = sorted.get(index - 1);
                        AiGeneratedResultRule current = sorted.get(index);
                        if (isInteger(previous.scoreMax()) && isInteger(current.scoreMin())
                                && current.scoreMin().compareTo(previous.scoreMax().add(BigDecimal.ONE)) == 0) {
                            adjusted.put(previous.resultCode(), current.scoreMin());
                        }
                    }
                    return adjusted.entrySet().stream();
                })
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        return source.stream().map(rule -> {
            BigDecimal adjustedMax = adjustedMaxMap.get(rule.resultCode());
            return Objects.isNull(adjustedMax) ? rule : new AiGeneratedResultRule(
                    rule.dimensionCode(), rule.resultCode(), rule.resultName(), rule.scoreMin(), adjustedMax,
                    rule.basicResultJson(), rule.deepResultJson(), rule.shareCopyJson(), rule.sortNo());
        }).toList();
    }

    private static boolean isInteger(BigDecimal value) {
        return value.stripTrailingZeros().scale() <= 0;
    }

    private static void requireText(String value, int maxLength, String fieldName) {
        if (!hasText(value) || value.length() > maxLength) {
            throw invalid(fieldName + "不能为空且不能超过 " + maxLength + " 个字符");
        }
    }

    private static void requireObject(JsonNode value, String fieldName) {
        if (Objects.isNull(value) || !value.isObject() || !value.path("text").isTextual()
                || value.path("text").textValue().isBlank()) {
            throw invalid(fieldName + "必须为包含非空 text 的 JSON 对象");
        }
    }

    private static void requireOptionalObject(JsonNode value, String fieldName) {
        if (Objects.nonNull(value) && !value.isNull()
                && (!value.isObject() || !value.path("text").isTextual()
                || value.path("text").textValue().isBlank())) {
            throw invalid(fieldName + "必须为包含非空 text 的 JSON 对象");
        }
    }

    private static void requireCode(String value, Pattern pattern, String fieldName) {
        if (!hasText(value) || !pattern.matcher(value).matches()) {
            throw invalid(fieldName + "只能使用 ASCII 英文字母、半角数字、中文汉字、下划线和短横线");
        }
    }

    private static String normalizedCode(String value) {
        return value.toLowerCase(Locale.ROOT);
    }

    private static boolean invalidSort(Integer sortNo) {
        return Objects.nonNull(sortNo) && sortNo < 0;
    }

    private static boolean hasText(String value) {
        return Objects.nonNull(value) && !value.isBlank();
    }

    private static IllegalArgumentException invalid(String message) {
        return new IllegalArgumentException("AI 返回内容校验失败：" + message);
    }
}
