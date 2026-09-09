package com.personalink.server.entity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;
/** t_legal_document 已发布协议；每种类型仅一份当前正文。 @author persona-link @since 2026-09-09 */
@Getter @Setter @TableName("t_legal_document")
public class LegalDocumentEntity extends BaseAssessmentEntity {
    /** 类型，参见 LegalDocumentType。 */ private Integer type;
    /** 标题。 */ private String title;
    /** 纯文本正文。 */ private String content;
    /** 发布版本号。 */ private Long version;
}
