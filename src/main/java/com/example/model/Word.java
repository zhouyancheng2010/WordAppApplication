package com.example.model;

import jakarta.persistence.*;

@Entity
public class Word {

    @Id
    private Long id;

    @Column(nullable = false)
    private String word;

    private Integer sortOrder;
    private String pronunciation;
    private String definition;
    private String synonyms;
    private String antonyms;
    private String example;
    private String root;
    private String suffix;

    @Column(length = 200)
    private String layer1;

    @Column(length = 200)
    private String layer2;

    @Column(length = 200)
    private String layer3;

    @Lob
    @Column(columnDefinition = "LONGTEXT")
    private String content;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getWord() { return word; }
    public void setWord(String word) { this.word = word; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
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
    public String getLayer1() { return layer1; }
    public void setLayer1(String layer1) { this.layer1 = layer1; }
    public String getLayer2() { return layer2; }
    public void setLayer2(String layer2) { this.layer2 = layer2; }
    public String getLayer3() { return layer3; }
    public void setLayer3(String layer3) { this.layer3 = layer3; }
}
