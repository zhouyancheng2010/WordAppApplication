package com.example.model;

import jakarta.persistence.*;

@Entity
public class Word {

    @Id
    private String word;
    private String pronunciation;
    private String definition;
    private String synonyms;
    private String antonyms;
    private String example;
    private String root;
    private String suffix;

    // 明确指定为 LONGTEXT，支持存储大文本
    @Lob
    @Column(columnDefinition = "LONGTEXT")
    private String content;

    public String getWord() { return word; }
    public void setWord(String word) { this.word = word; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getPronunciation() { return pronunciation; }
    public void setPronunciation(String pronunciation) { this.pronunciation = pronunciation; }
    public String getDefinition() { return definition; }
    public void setDefinition(String definition) { this.definition = definition; }
    public String getSynonyms() { return synonyms; }
    public void setSynonyms(String synonyms) { this.synonyms = synonyms; }
    public String getAntonyms() { return antonyms; }
    public void setAntonyms(String antonyms) { this.antonyms = antonyms; }
    public String getExample() { return example; }
    public void setExample(String example) { this.example = example; }
    public String getRoot() { return root; }
    public void setRoot(String root) { this.root = root; }
    public String getSuffix() { return suffix; }
    public void setSuffix(String suffix) { this.suffix = suffix; }
}
