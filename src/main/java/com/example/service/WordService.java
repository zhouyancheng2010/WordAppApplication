package com.example.service;

import com.example.model.Word;
import com.example.repository.WordRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Transactional
public class WordService {

    @Autowired
    private WordRepository wordRepository;

    public Page<Word> getAllWords(Pageable pageable) {
        return wordRepository.findAll(pageable);
    }

    public Optional<Word> getWordById(Long id) {
        return wordRepository.findById(id);
    }

    public Optional<Word> getWordByWordAndSortOrder(String word, Integer sortOrder) {
        return wordRepository.findByWordAndSortOrder(word, sortOrder);
    }

    public Word getWord(String word) {
        return wordRepository.findByWord(word).orElse(null);
    }

    public void saveWord(Word word) {
        wordRepository.save(word);
    }

    public Page<Word> searchWords(String keyword, Pageable pageable) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return wordRepository.findAll(pageable);
        }
        return wordRepository.findByWordContainingIgnoreCase(keyword.trim(), pageable);
    }

    public List<Word> getAllWordsBySortOrder() {
        return wordRepository.findAll(Sort.by(Sort.Direction.ASC, "sortOrder"));
    }

    private String extractSortOrderFromContent(String content) {
        if (content == null || content.isEmpty()) {
            return null;
        }

        List<Pattern> patterns = Arrays.asList(
                Pattern.compile("^(\\d{3})\\s+\\*\\*"),
                Pattern.compile("^\\*\\*(\\d{3})\\s+"),
                Pattern.compile("^\\*\\*(\\d{3})\\s+([a-zA-Z]+)\\*\\*"),
                Pattern.compile("^(\\d{3})\\s+([a-zA-Z]+)"),
                Pattern.compile("^\\*\\*([a-zA-Z]+)\\*\\*\\s+(\\d{3})"),
                Pattern.compile("^([a-zA-Z]+)\\s+(\\d{3})")
        );

        for (Pattern pattern : patterns) {
            Matcher matcher = pattern.matcher(content.trim());
            if (matcher.find()) {
                if (pattern.toString().contains("\\d{3}.*[a-zA-Z]")) {
                    return matcher.group(1);
                } else if (pattern.toString().contains("[a-zA-Z].*\\d{3}")) {
                    return matcher.group(2);
                } else {
                    return matcher.group(1);
                }
            }
        }

        return null;
    }

    public List<Map<String, Object>> buildCatalogStructure() {
        System.out.println("\ud83d\udd0d \u5f00\u59cb\u6784\u5efa\u76ee\u5f55\u7ed3\u6784...");
        List<Map<String, Object>> structure = new ArrayList<>();

        try {
            String content = readFileMd();
            if (content == null || content.isEmpty()) {
                System.err.println("\u274c file.md\u6587\u4ef6\u4e0d\u5b58\u5728\u6216\u4e3a\u7a7a");
                return structure;
            }

            List<Word> allWords = wordRepository.findAll();
            Map<String, Word> wordMap = new HashMap<>();

            for (Word word : allWords) {
                String sortOrderFromContent = extractSortOrderFromContent(word.getContent());
                String wordName = word.getWord();

                if (sortOrderFromContent != null && wordName != null) {
                    String key = sortOrderFromContent + "|" + wordName.toLowerCase();
                    wordMap.put(key, word);
                    System.out.println("\u6620\u5c04: " + key + " -> DB ID: " + word.getId());
                }
            }

            System.out.println("\ud83d\udcca \u6570\u636e\u5e93\u5355\u8bcd\u6570\u91cf: " + allWords.size());
            System.out.println("\ud83d\udd17 \u6210\u529f\u5efa\u7acb\u6620\u5c04: " + wordMap.size() + " \u4e2a");

            String[] lines = content.split("\\r?\\n");

            List<Map<String, Object>> stack = new ArrayList<>();
            Map<String, Object> root = new LinkedHashMap<>();
            root.put("title", "root");
            root.put("level", 0);
            root.put("children", structure);
            root.put("words", new ArrayList<>());
            stack.add(root);

            Pattern headingPattern = Pattern.compile("^(#{1,6})\\s+(.+)$");

            List<Pattern> wordPatterns = Arrays.asList(
                    Pattern.compile("^\\*\\*(\\d{3})\\s+(.+?)\\*\\*"),
                    Pattern.compile("^(\\d{3})\\s+\\*\\*(.+?)\\*\\*"),
                    Pattern.compile("^(\\d{3})\\s+([a-zA-Z]+(?:[-'][a-zA-Z]+)*)"),
                    Pattern.compile("^\\*\\*(.+?)\\*\\*\\s+(\\d{3})"),
                    Pattern.compile("^([a-zA-Z]+(?:[-'][a-zA-Z]+)*)\\s+(\\d{3})"),
                    Pattern.compile("^\\*\\*(\\d{3})\\s+([a-zA-Z]+(?:[-'][a-zA-Z]+)*)\\*\\*"),
                    Pattern.compile("^([a-zA-Z]+(?:[-'][a-zA-Z]+)*)\\s+(\\d{3})\\s"),
                    Pattern.compile("^\\*\\*([a-zA-Z]+(?:[-'][a-zA-Z]+)*)\\*\\*\\s+(\\d{3})")
            );

            int wordCount = 0;
            int headingCount = 0;

            for (int lineNum = 0; lineNum < lines.length; lineNum++) {
                String line = lines[lineNum];
                String trimmedLine = line.trim();

                if (trimmedLine.isEmpty() || trimmedLine.startsWith("---") || trimmedLine.startsWith("----")) {
                    continue;
                }

                Matcher headingMatcher = headingPattern.matcher(trimmedLine);
                if (headingMatcher.find()) {
                    String hashes = headingMatcher.group(1);
                    String title = headingMatcher.group(2).trim();
                    int markdownLevel = hashes.length();
                    headingCount++;

                    int normalizedLevel = Math.max(1, markdownLevel - 2);
                    if (normalizedLevel > 6) normalizedLevel = 6;

                    Map<String, Object> newNode = new LinkedHashMap<>();
                    newNode.put("title", title);
                    newNode.put("level", normalizedLevel);
                    newNode.put("children", new ArrayList<Map<String, Object>>());
                    newNode.put("words", new ArrayList<Map<String, Object>>());

                    while (stack.size() > normalizedLevel) {
                        stack.remove(stack.size() - 1);
                    }

                    Map<String, Object> parent = stack.get(stack.size() - 1);
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> children = (List<Map<String, Object>>) parent.get("children");
                    children.add(newNode);

                    stack.add(newNode);

                    System.out.println("  ".repeat(normalizedLevel - 1) + "\ud83d\udcc4 " + normalizedLevel + "\u7ea7: " + title);
                    continue;
                }

                String number = null;
                String wordName = null;

                for (Pattern pattern : wordPatterns) {
                    Matcher matcher = pattern.matcher(trimmedLine);
                    if (matcher.find()) {
                        String patternStr = pattern.toString();
                        if (patternStr.contains("\\*\\*(\\d{3})\\s+(.+?)\\*\\*") && !patternStr.contains("[a-zA-Z]")) {
                            number = matcher.group(1);
                            wordName = matcher.group(2).trim();
                        } else if (patternStr.contains("(\\d{3})\\s+\\*\\*(.+?)\\*\\*")) {
                            number = matcher.group(1);
                            wordName = matcher.group(2).trim();
                        } else if (patternStr.contains("(\\d{3})\\s+([a-zA-Z]+") && !patternStr.contains("\\*\\*")) {
                            number = matcher.group(1);
                            wordName = matcher.group(2).trim();
                        } else if (patternStr.contains("\\*\\*(.+?)\\*\\*\\s+(\\d{3})")) {
                            wordName = matcher.group(1).trim();
                            number = matcher.group(2);
                        } else if (patternStr.contains("([a-zA-Z]+") && patternStr.contains("\\s+(\\d{3})") && !patternStr.contains("\\*\\*")) {
                            wordName = matcher.group(1).trim();
                            number = matcher.group(2);
                        } else if (patternStr.contains("\\*\\*(\\d{3})\\s+([a-zA-Z]+") && patternStr.contains("\\*\\*")) {
                            number = matcher.group(1);
                            wordName = matcher.group(2).trim();
                        } else if (patternStr.contains("([a-zA-Z]+)\\s+(\\d{3})\\s")) {
                            wordName = matcher.group(1).trim();
                            number = matcher.group(2);
                        } else if (patternStr.contains("\\*\\*([a-zA-Z]+)\\*\\*\\s+(\\d{3})")) {
                            wordName = matcher.group(1).trim();
                            number = matcher.group(2);
                        }
                        break;
                    }
                }

                if (number != null && wordName != null && wordName.length() > 0 && wordName.length() < 50) {
                    wordCount++;
                    int sortOrderNum;
                    try {
                        sortOrderNum = Integer.parseInt(number);
                    } catch (NumberFormatException e) {
                        sortOrderNum = wordCount;
                    }

                    String key = number + "|" + wordName.toLowerCase();
                    Word foundWord = wordMap.get(key);

                    Map<String, Object> wordInfo = new LinkedHashMap<>();
                    wordInfo.put("number", number);
                    wordInfo.put("word", wordName);
                    wordInfo.put("sortOrder", sortOrderNum);

                    if (foundWord != null) {
                        wordInfo.put("id", foundWord.getId());
                        System.out.println("\u2705 \u5339\u914d\u6210\u529f: " + key + " -> DB ID: " + foundWord.getId());
                    } else {
                        wordInfo.put("id", null);
                        System.out.println("\u26a0\ufe0f \u672a\u627e\u5230\u6620\u5c04: " + key);
                    }

                    Map<String, Object> currentParent = stack.get(stack.size() - 1);
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> words = (List<Map<String, Object>>) currentParent.get("words");
                    words.add(wordInfo);
                }
            }

            System.out.println("\u2705 \u76ee\u5f55\u6784\u5efa\u5b8c\u6210\uff01");
            System.out.println("  \u6807\u9898\u603b\u6570: " + headingCount + " \u4e2a");
            System.out.println("  \u5355\u8bcd\u603b\u6570: " + wordCount + " \u4e2a");
            System.out.println("  \u4e00\u7ea7\u5206\u7c7b: " + structure.size() + " \u4e2a");

            return structure;
        } catch (Exception e) {
            System.err.println("\u274c \u6784\u5efa\u76ee\u5f55\u5931\u8d25: " + e.getMessage());
            e.printStackTrace();
            return new ArrayList<>();
        }
    }


    private String readFileMd() {
        try {
            List<Path> possiblePaths = Arrays.asList(
                    Paths.get(System.getProperty("user.dir"), "src", "main", "resources", "file.md"),
                    Paths.get(System.getProperty("user.dir"), "file.md"),
                    Paths.get("src", "main", "resources", "file.md"),
                    Paths.get("file.md")
            );

            for (Path filePath : possiblePaths) {
                if (Files.exists(filePath)) {
                    System.out.println("\ud83d\udcc2 \u627e\u5230\u6587\u4ef6: " + filePath.toAbsolutePath());
                    String content = Files.readString(filePath, StandardCharsets.UTF_8);
                    System.out.println("\ud83d\udcd6 \u6587\u4ef6\u5927\u5c0f: " + content.length() + " \u5b57\u7b26");
                    return content;
                }
            }

            ClassPathResource resource = new ClassPathResource("file.md");
            if (resource.exists()) {
                System.out.println("\ud83d\udcc2 \u4ece classpath \u8bfb\u53d6 file.md");
                return new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            }

            System.err.println("\u274c \u65e0\u6cd5\u627e\u5230 file.md \u6587\u4ef6");
            return null;
        } catch (Exception e) {
            System.err.println("\u274c \u8bfb\u53d6 file.md \u5931\u8d25: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }
}
