package com.example.model;

import jakarta.persistence.*;
import java.util.Objects;

@Entity
@Table(name = "word", indexes = {
        @Index(name = "idx_word", columnList = "word"),
        @Index(name = "idx_sort_order", columnList = "sort_order")
})
public class Word {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String word;

    @Column(name = "sort_order")
    private Integer sortOrder;

    private String pronunciation;

    @Column(length = 1000)
    private String definition;

    private String synonyms;
    private String antonyms;

    @Column(length = 500)
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

    // 无参构造器
    public Word() {}

    // 全参构造器
    public Word(Long id, String word, Integer sortOrder, String pronunciation, String definition,
                String synonyms, String antonyms, String example, String root, String suffix,
                String layer1, String layer2, String layer3, String content) {
        this.id = id;
        this.word = word;
        this.sortOrder = sortOrder;
        this.pronunciation = pronunciation;
        this.definition = definition;
        this.synonyms = synonyms;
        this.antonyms = antonyms;
        this.example = example;
        this.root = root;
        this.suffix = suffix;
        this.layer1 = layer1;
        this.layer2 = layer2;
        this.layer3 = layer3;
        this.content = content;
    }

    // Getter 和 Setter
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getWord() {
        return word;
    }

    public void setWord(String word) {
        this.word = word;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }

    public String getPronunciation() {
        return pronunciation;
    }

    public void setPronunciation(String pronunciation) {
        this.pronunciation = pronunciation;
    }

    public String getDefinition() {
        return definition;
    }

    public void setDefinition(String definition) {
        this.definition = definition;
    }

    public String getSynonyms() {
        return synonyms;
    }

    public void setSynonyms(String synonyms) {
        this.synonyms = synonyms;
    }

    public String getAntonyms() {
        return antonyms;
    }

    public void setAntonyms(String antonyms) {
        this.antonyms = antonyms;
    }

    public String getExample() {
        return example;
    }

    public void setExample(String example) {
        this.example = example;
    }

    public String getRoot() {
        return root;
    }

    public void setRoot(String root) {
        this.root = root;
    }

    public String getSuffix() {
        return suffix;
    }

    public void setSuffix(String suffix) {
        this.suffix = suffix;
    }

    public String getLayer1() {
        return layer1;
    }

    public void setLayer1(String layer1) {
        this.layer1 = layer1;
    }

    public String getLayer2() {
        return layer2;
    }

    public void setLayer2(String layer2) {
        this.layer2 = layer2;
    }

    public String getLayer3() {
        return layer3;
    }

    public void setLayer3(String layer3) {
        this.layer3 = layer3;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Word word1 = (Word) o;
        return Objects.equals(id, word1.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    @Override
    public String toString() {
        return "Word{" +
                "id=" + id +
                ", word='" + word + '\'' +
                ", sortOrder=" + sortOrder +
                '}';
    }
}