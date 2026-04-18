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
            Map<String, Object> currentLayer1 = null;
            Map<String, Object> currentLayer2 = null;
            Map<String, Object> currentLayer3 = null;
            Set<String> seenLayer1 = new HashSet<>();
            Set<String> seenLayer2 = new HashSet<>();
            Set<String> seenLayer3 = new HashSet<>();

            String[] lines = content.split("\\r?\\n");
            String currentLayer1Title = null;
            String currentLayer2Title = null;
            String currentLayer3Title = null;

            for (String line : lines) {
                String trimmedLine = line.trim();

                Pattern layer1Pattern = Pattern.compile("^####\\s+(.+)");
                Matcher layer1Matcher = layer1Pattern.matcher(trimmedLine);

                Pattern layer2Pattern = Pattern.compile("^#####\\s+(.+)");
                Matcher layer2Matcher = layer2Pattern.matcher(trimmedLine);

                Pattern layer3Pattern1 = Pattern.compile("^######\\s+(.+)");
                Pattern layer3Pattern2 = Pattern.compile("^(\\d+)\\.\\s+(.+?\\（\\d+词\\）)");
                Matcher layer3Matcher1 = layer3Pattern1.matcher(trimmedLine);
                Matcher layer3Matcher2 = layer3Pattern2.matcher(trimmedLine);

                Pattern wordPattern = Pattern.compile("^(\\d{3})\\s+(?:\\*\\*)?(.+?)(?:\\*\\*)?\\s+/");
                Matcher wordMatcher = wordPattern.matcher(trimmedLine);

                if (layer1Matcher.find()) {
                    currentLayer1Title = layer1Matcher.group(1).trim();
                    if (!seenLayer1.contains(currentLayer1Title)) {
                        seenLayer1.add(currentLayer1Title);
                        currentLayer1 = new LinkedHashMap<>();
                        currentLayer1.put("title", currentLayer1Title);
                        currentLayer1.put("children", new ArrayList<Map<String, Object>>());
                        currentLayer1.put("words", new ArrayList<Map<String, Object>>());
                        structure.add(currentLayer1);
                        seenLayer2.clear();
                        seenLayer3.clear();
                        currentLayer2 = null;
                        currentLayer3 = null;
                        System.out.println("📑 L1: " + currentLayer1Title);
                    }
                } else if (layer2Matcher.find() && currentLayer1 != null) {
                    currentLayer2Title = layer2Matcher.group(1).trim();
                    String layer2Key = currentLayer1Title + "|" + currentLayer2Title;
                    if (!seenLayer2.contains(layer2Key)) {
                        seenLayer2.add(layer2Key);
                        currentLayer2 = new LinkedHashMap<>();
                        currentLayer2.put("title", currentLayer2Title);
                        currentLayer2.put("children", new ArrayList<Map<String, Object>>());
                        currentLayer2.put("words", new ArrayList<Map<String, Object>>());
                        @SuppressWarnings("unchecked")
                        List<Map<String, Object>> layer1Children = (List<Map<String, Object>>) currentLayer1.get("children");
                        layer1Children.add(currentLayer2);
                        seenLayer3.clear();
                        currentLayer3 = null;
                        System.out.println("  📂 L2: " + currentLayer2Title);
                    }
                } else if (currentLayer2 != null) {
                    String layer3Text = null;
                    if (layer3Matcher1.find()) {
                        layer3Text = layer3Matcher1.group(1).trim();
                    } else if (layer3Matcher2.find()) {
                        layer3Text = layer3Matcher2.group(1) + ". " + layer3Matcher2.group(2).trim();
                    }

                    if (layer3Text != null) {
                        currentLayer3Title = layer3Text;
                        String layer3Key = currentLayer2Title + "|" + currentLayer3Title;
                        if (!seenLayer3.contains(layer3Key)) {
                            seenLayer3.add(layer3Key);
                            currentLayer3 = new LinkedHashMap<>();
                            currentLayer3.put("title", currentLayer3Title);
                            currentLayer3.put("words", new ArrayList<Map<String, Object>>());
                            @SuppressWarnings("unchecked")
                            List<Map<String, Object>> layer2Children = (List<Map<String, Object>>) currentLayer2.get("children");
                            layer2Children.add(currentLayer3);
                            System.out.println("    📄 L3: " + currentLayer3Title);
                        }
                    }
                }

                if (wordMatcher.find()) {
                    String number = wordMatcher.group(1);
                    String wordName = wordMatcher.group(2).trim();

                    Map<String, Object> wordInfo = new HashMap<>();
                    wordInfo.put("number", number);
                    wordInfo.put("word", wordName);
                    wordInfo.put("layer1", currentLayer1Title);
                    wordInfo.put("layer2", currentLayer2Title);
                    wordInfo.put("layer3", currentLayer3Title);

                    try {
                        Optional<Word> wordOpt = wordRepository.findByWord(wordName);
                        if (wordOpt.isPresent()) {
                            Word word = wordOpt.get();
                            wordInfo.put("id", word.getId());
                            word.setLayer1(currentLayer1Title);
                            word.setLayer2(currentLayer2Title);
                            word.setLayer3(currentLayer3Title);
                            wordRepository.save(word);
                        } else {
                            wordInfo.put("id", null);
                        }
                    } catch (Exception e) {
                        wordInfo.put("id", null);
                    }

                    if (currentLayer3 != null) {
                        @SuppressWarnings("unchecked")
                        List<Map<String, Object>> words = (List<Map<String, Object>>) currentLayer3.get("words");
                        words.add(wordInfo);
                    } else if (currentLayer2 != null) {
                        @SuppressWarnings("unchecked")
                        List<Map<String, Object>> words = (List<Map<String, Object>>) currentLayer2.get("words");
                        words.add(wordInfo);
                    } else if (currentLayer1 != null) {
                        @SuppressWarnings("unchecked")
                        List<Map<String, Object>> words = (List<Map<String, Object>>) currentLayer1.get("words");
                        words.add(wordInfo);
                    }
                }
            }

            System.out.println("✅ 目录结构解析完成，共 " + structure.size() + " 个一级分类");
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
