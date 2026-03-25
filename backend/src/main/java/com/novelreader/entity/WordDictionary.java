package com.novelreader.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

@Data
@TableName("word_dictionary")
public class WordDictionary {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("user_id")
    private Long userId;

    private String word;
    private String reading;
    private String meaning;

    @TableField("example_sentence")
    private String exampleSentence;

    @TableField("created_at")
    private LocalDateTime createdAt;
}
