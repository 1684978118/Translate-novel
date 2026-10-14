package com.novelreader.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

@Data
@TableName("paragraphs")
public class Paragraph {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("chapter_id")
    private Long chapterId;

    @TableField("paragraph_order")
    private Integer paragraphOrder;

    @TableField("block_type")
    private String blockType;

    @TableField("original_text")
    private String originalText;

    @TableField("segmented_text")
    private String segmentedText;

    private String translation;

    @TableField("furigana_text")
    private String furiganaText;

    @TableField("image_url")
    private String imageUrl;

    @TableField("created_at")
    private LocalDateTime createdAt;
}
