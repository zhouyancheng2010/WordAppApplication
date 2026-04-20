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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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
            String content = readFileMd();
            if (content == null || content.isEmpty()) {
                System.err.println("❌ file.md文件不存在或为空");
                return new ArrayList<>();
            }

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

            Pattern layer1Pattern = Pattern.compile("^####\\s+(.+)");
            Pattern layer2Pattern = Pattern.compile("^#####\\s+(.+)");
            Pattern layer3Pattern = Pattern.compile("^######\\s+(.+)");
            Pattern layer3AltPattern = Pattern.compile("^(.+?)\\s*\\（\\d+词\\）$");
            Pattern wordPattern1 = Pattern.compile("^(\\d{3})\\s+\\*\\*(.+?)\\*\\*\\s+(/\\S+)?\\s*(.+)?$");
            Pattern wordPattern2 = Pattern.compile("^\\*\\*(\\d{3})\\s+(.+?)\\*\\*\\s+(/\\S+)?\\s*(.+)?$");

            int wordCount = 0;
            int totalWords = 0;

            for (String line : lines) {
                String trimmedLine = line.trim();

                if (trimmedLine.isEmpty() || trimmedLine.startsWith("------") || trimmedLine.startsWith("---")) {
                    continue;
                }

                Matcher layer1Matcher = layer1Pattern.matcher(trimmedLine);
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
                    continue;
                }

                Matcher layer2Matcher = layer2Pattern.matcher(trimmedLine);
                if (layer2Matcher.find()) {
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
                    continue;
                }

                String layer3Text = null;
                Matcher layer3Matcher = layer3Pattern.matcher(trimmedLine);
                if (layer3Matcher.find()) {
                    layer3Text = layer3Matcher.group(1).trim();
                } else {
                    Matcher layer3AltMatcher = layer3AltPattern.matcher(trimmedLine);
                    if (layer3AltMatcher.find()) {
                        String candidate = layer3AltMatcher.group(1).trim();
                        if (candidate.matches("^[\\u4e00-\\u9fa5a-zA-Z/\\s、]+$")) {
                            layer3Text = candidate;
                        }
                    }
                }

                if (layer3Text != null && currentLayer2 != null) {
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
                    continue;
                }

                String number = null;
                String wordName = null;

                Matcher wordMatcher1 = wordPattern1.matcher(trimmedLine);
                if (wordMatcher1.find()) {
                    number = wordMatcher1.group(1);
                    wordName = wordMatcher1.group(2).trim();
                } else {
                    Matcher wordMatcher2 = wordPattern2.matcher(trimmedLine);
                    if (wordMatcher2.find()) {
                        number = wordMatcher2.group(1);
                        wordName = wordMatcher2.group(2).trim();
                    }
                }

                if (number != null && wordName != null) {
                    totalWords++;

                    Map<String, Object> wordInfo = new LinkedHashMap<>();
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
                        wordCount++;
                    } else if (currentLayer2 != null) {
                        @SuppressWarnings("unchecked")
                        List<Map<String, Object>> words = (List<Map<String, Object>>) currentLayer2.get("words");
                        words.add(wordInfo);
                        wordCount++;
                    } else if (currentLayer1 != null) {
                        @SuppressWarnings("unchecked")
                        List<Map<String, Object>> words = (List<Map<String, Object>>) currentLayer1.get("words");
                        words.add(wordInfo);
                        wordCount++;
                    }
                }
            }

            System.out.println("✅ 目录结构解析完成，共 " + structure.size() + " 个一级分类");
            System.out.println("📊 统计: 扫描 " + totalWords + " 个单词，归类 " + wordCount + " 个");
            return structure;
        } catch (Exception e) {
            System.err.println("❌ 构建目录结构失败: " + e.getMessage());
            e.printStackTrace();
            return new ArrayList<>();
        }
    }


    private String readFileMd() {
        try {
            Path filePath = Paths.get(System.getProperty("user.dir"), "src", "main", "resources", "file.md");

            if (Files.exists(filePath)) {
                System.out.println("📂 从文件系统读取: " + filePath);
                return Files.readString(filePath, StandardCharsets.UTF_8);
            }

            ClassPathResource resource = new ClassPathResource("file.md");
            if (resource.exists()) {
                System.out.println("📂 从 classpath 读取 file.md");
                return new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            }

            return null;
        } catch (Exception e) {
            System.err.println("❌ 读取 file.md 失败: " + e.getMessage());
            return null;
        }
    }
}
