package com.novelreader.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

@Data
@TableName("reading_progress")
public class ReadingProgress {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("user_id")
    private Long userId;

    @TableField("book_id")
    private Long bookId;

    @TableField("chapter_id")
    private Long chapterId;

    @TableField("paragraph_id")
    private Long paragraphId;

    @TableField("scroll_position")
    private Integer scrollPosition;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
