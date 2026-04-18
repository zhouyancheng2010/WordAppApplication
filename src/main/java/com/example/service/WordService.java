package com.example.service;

import com.example.model.Word;
import com.example.repository.WordRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class WordService {

    @Autowired
    private WordRepository wordRepository;

    public Page<Word> getAllWords(Pageable pageable) {
        return wordRepository.findAll(pageable);
    }

    public Word getWord(String word) {
        return wordRepository.findByWord(word).orElse(null);
    }

    public void addWord(Word word) {
        wordRepository.save(word);
    }

    public Page<Word> searchWords(String keyword, Pageable pageable) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return wordRepository.findAll(pageable);
        }
        return wordRepository.findByWordContainingIgnoreCase(keyword.trim(), pageable);
    }

    public List<Word> getAllWordsBySortOrder() {
        List<Word> words = wordRepository.findAll();
        return words.stream()
                .sorted((w1, w2) -> {
                    if (w1.getSortOrder() == null && w2.getSortOrder() == null) return 0;
                    if (w1.getSortOrder() == null) return 1;
                    if (w2.getSortOrder() == null) return -1;
                    return w1.getSortOrder().compareTo(w2.getSortOrder());
                })
                .collect(Collectors.toList());
    }

    public List<Map<String, Object>> buildCatalogStructure() {
        try {
            ClassPathResource resource = new ClassPathResource("file.md");
            if (!resource.exists()) {
                System.err.println("❌ file.md文件不存在");
                return new ArrayList<>();
            }

            String content = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            System.out.println("✅ 成功读取file.md，内容长度: " + content.length());

            List<Map<String, Object>> structure = new ArrayList<>();
            Map<String, Object> currentLayer = null;
            Map<String, Object> currentCategory = null;
            Set<String> seenLayers = new HashSet<>();
            Set<String> seenCategories = new HashSet<>();

            String[] lines = content.split("\\r?\\n");
            String currentLayerTitle = null;
            String currentCategoryTitle = null;

            for (String line : lines) {
                Pattern layerPattern = Pattern.compile("^####\\s+(.+)");
                Matcher layerMatcher = layerPattern.matcher(line);

                Pattern categoryPattern = Pattern.compile("^#####\\s+(.+)");
                Matcher categoryMatcher = categoryPattern.matcher(line);

                Pattern wordPattern = Pattern.compile("^(\\d{3})\\s+\\*\\*(.+?)\\*\\*");
                Matcher wordMatcher = wordPattern.matcher(line);

                if (layerMatcher.find()) {
                    currentLayerTitle = layerMatcher.group(1).trim();
                    if (!seenLayers.contains(currentLayerTitle)) {
                        seenLayers.add(currentLayerTitle);
                        currentLayer = new LinkedHashMap<>();
                        currentLayer.put("title", currentLayerTitle);
                        currentLayer.put("children", new ArrayList<Map<String, Object>>());
                        structure.add(currentLayer);
                        seenCategories.clear();
                        currentCategory = null;
                        System.out.println("📑 解析到层级: " + currentLayerTitle);
                    }
                } else if (categoryMatcher.find() && currentLayer != null) {
                    currentCategoryTitle = categoryMatcher.group(1).trim();
                    String catKey = currentLayerTitle + "|" + currentCategoryTitle;
                    if (!seenCategories.contains(catKey)) {
                        seenCategories.add(catKey);
                        currentCategory = new LinkedHashMap<>();
                        currentCategory.put("title", currentCategoryTitle);
                        currentCategory.put("words", new ArrayList<Map<String, Object>>());
                        @SuppressWarnings("unchecked")
                        List<Map<String, Object>> children = (List<Map<String, Object>>) currentLayer.get("children");
                        children.add(currentCategory);
                        System.out.println("  📂 解析到分类: " + currentCategoryTitle);
                    }
                } else if (wordMatcher.find() && currentCategory != null) {
                    String number = wordMatcher.group(1);
                    String wordName = wordMatcher.group(2).trim();

                    Map<String, Object> wordInfo = new HashMap<>();
                    wordInfo.put("number", number);
                    wordInfo.put("word", wordName);

                    try {
                        Optional<Word> wordOpt = wordRepository.findByWord(wordName);
                        if (wordOpt.isPresent()) {
                            wordInfo.put("id", wordOpt.get().getId());
                        } else {
                            wordInfo.put("id", null);
                            System.out.println("  ⚠️ 单词未在数据库中找到: " + wordName);
                        }
                    } catch (Exception e) {
                        System.err.println("  ❌ 查询单词失败: " + wordName + ", 错误: " + e.getMessage());
                        wordInfo.put("id", null);
                    }

                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> words = (List<Map<String, Object>>) currentCategory.get("words");
                    words.add(wordInfo);
                }
            }

            System.out.println("✅ 目录结构解析完成，共 " + structure.size() + " 个层级");
            return structure;
        } catch (IOException e) {
            System.err.println("❌ 构建目录结构失败: " + e.getMessage());
            e.printStackTrace();
            return new ArrayList<>();
        } catch (Exception e) {
            System.err.println("❌ 解析过程中发生未知错误: " + e.getMessage());
            e.printStackTrace();
            return new ArrayList<>();
        }
    }
}
