package com.novelreader.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

@Data
@TableName("books")
public class Book {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String title;
    private String author;

    @TableField("cover_url")
    private String coverUrl;

    @TableField("source_type")
    private String sourceType;

    @TableField("file_path")
    private String filePath;

    @TableField("upload_user_id")
    private Long uploadUserId;

    private String status;

    @TableField("word_count")
    private Integer wordCount;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
