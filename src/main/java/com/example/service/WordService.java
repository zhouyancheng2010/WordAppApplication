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
import java.util.stream.Collectors;

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

    public List<Map<String, Object>> buildCatalogStructure() {
        System.out.println("🔍 开始构建目录结构...");
        List<Map<String, Object>> structure = new ArrayList<>();

        try {
            String content = readFileMd();
            if (content == null || content.isEmpty()) {
                System.err.println("❌ file.md文件不存在或为空");
                return structure;
            }

            // 获取所有单词用于ID映射
            List<Word> allWords = wordRepository.findAll();
            Map<String, Word> wordMap = new HashMap<>();
            for (Word word : allWords) {
                String key = word.getWord().toLowerCase() + "|" + word.getSortOrder();
                wordMap.put(key, word);
            }
            System.out.println("📊 数据库单词数量: " + allWords.size());

            String[] lines = content.split("\\r?\\n");

            // 层级栈 - 支持最多8级标题
            List<Map<String, Object>> stack = new ArrayList<>();
            Map<String, Object> root = new LinkedHashMap<>();
            root.put("title", "root");
            root.put("level", 0);
            root.put("children", structure);
            root.put("words", new ArrayList<>());
            stack.add(root);

            // 标题正则：支持1-8个#
            Pattern headingPattern = Pattern.compile("^(#{1,8})\\s+(.+)$");

            // ==================== 单词正则 - 支持6种格式 ====================
            // 格式1: **001 science** /ˈsaɪəns/
            // 格式2: 001 **science** /ˈsaɪəns/
            // 格式3: 001 science /ˈsaɪəns/
            // 格式4: **science** 001 /ˈsaɪəns/
            // 格式5: science 001 /ˈsaɪəns/
            // 格式6: 001-science 或 001_science (连字符/下划线连接)

            List<Pattern> wordPatterns = Arrays.asList(
                    // 格式1: **001 science**
                    Pattern.compile("^\\*\\*(\\d{3})\\s+(.+?)\\*\\*"),
                    // 格式2: 001 **science**
                    Pattern.compile("^(\\d{3})\\s+\\*\\*(.+?)\\*\\*"),
                    // 格式3: 001 science
                    Pattern.compile("^(\\d{3})\\s+([a-zA-Z]+(?:[-'][a-zA-Z]+)*)"),
                    // 格式4: **science** 001
                    Pattern.compile("^\\*\\*(.+?)\\*\\*\\s+(\\d{3})"),
                    // 格式5: science 001
                    Pattern.compile("^([a-zA-Z]+(?:[-'][a-zA-Z]+)*)\\s+(\\d{3})"),
                    // 格式6: 001-science 或 001_science
                    Pattern.compile("^(\\d{3})[-_]([a-zA-Z]+(?:[-'][a-zA-Z]+)*)")
            );

            int wordCount = 0;
            int headingCount = 0;

            for (int lineNum = 0; lineNum < lines.length; lineNum++) {
                String line = lines[lineNum];
                String trimmedLine = line.trim();

                if (trimmedLine.isEmpty() || trimmedLine.startsWith("---") || trimmedLine.startsWith("----")) {
                    continue;
                }

                // ==================== 处理标题 ====================
                Matcher headingMatcher = headingPattern.matcher(trimmedLine);
                if (headingMatcher.find()) {
                    String hashes = headingMatcher.group(1);
                    String title = headingMatcher.group(2).trim();
                    int level = hashes.length();
                    headingCount++;

                    // 创建新节点
                    Map<String, Object> newNode = new LinkedHashMap<>();
                    newNode.put("title", title);
                    newNode.put("level", level);
                    newNode.put("children", new ArrayList<Map<String, Object>>());
                    newNode.put("words", new ArrayList<Map<String, Object>>());

                    // 调整栈到正确层级
                    while (stack.size() > level) {
                        stack.remove(stack.size() - 1);
                    }

                    // 添加到父节点
                    Map<String, Object> parent = stack.get(stack.size() - 1);
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> children = (List<Map<String, Object>>) parent.get("children");
                    children.add(newNode);

                    // 将新节点入栈
                    stack.add(newNode);

                    // 打印日志
                    String indent = String.join("", Collections.nCopies(level - 1, "  "));
                    System.out.println(indent + "📑 " + level + "级: " + title);
                    continue;
                }

                // ==================== 处理单词 ====================
                String number = null;
                String wordName = null;

                // 尝试所有格式
                for (Pattern pattern : wordPatterns) {
                    Matcher matcher = pattern.matcher(trimmedLine);
                    if (matcher.find()) {
                        String patternStr = pattern.toString();
                        if (patternStr.contains("\\*\\*(\\d{3})\\s+(.+?)\\*\\*")) {
                            // 格式1: **001 science**
                            number = matcher.group(1);
                            wordName = matcher.group(2).trim();
                        } else if (patternStr.contains("(\\d{3})\\s+\\*\\*(.+?)\\*\\*")) {
                            // 格式2: 001 **science**
                            number = matcher.group(1);
                            wordName = matcher.group(2).trim();
                        } else if (patternStr.contains("(\\d{3})\\s+([a-zA-Z]+")) {
                            // 格式3: 001 science
                            number = matcher.group(1);
                            wordName = matcher.group(2).trim();
                        } else if (patternStr.contains("\\*\\*(.+?)\\*\\*\\s+(\\d{3})")) {
                            // 格式4: **science** 001
                            wordName = matcher.group(1).trim();
                            number = matcher.group(2);
                        } else if (patternStr.contains("([a-zA-Z]+") && patternStr.contains("\\s+(\\d{3})")) {
                            // 格式5: science 001
                            wordName = matcher.group(1).trim();
                            number = matcher.group(2);
                        } else if (patternStr.contains("(\\d{3})[-_]([a-zA-Z]+")) {
                            // 格式6: 001-science 或 001_science
                            number = matcher.group(1);
                            wordName = matcher.group(2).trim();
                        }
                        break;
                    }
                }

                // 额外的宽松匹配：匹配行首的数字
                if (number == null || wordName == null) {
                    Pattern loosePattern = Pattern.compile("^(\\d{3})\\s+(\\S+)");
                    Matcher looseMatcher = loosePattern.matcher(trimmedLine);
                    if (looseMatcher.find()) {
                        number = looseMatcher.group(1);
                        String candidate = looseMatcher.group(2);
                        // 去除可能的星号和标点
                        wordName = candidate.replaceAll("[*_，,。；;]", "").trim();
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

                    // 查找数据库中的单词
                    String key = wordName.toLowerCase() + "|" + sortOrderNum;
                    Word foundWord = wordMap.get(key);

                    Map<String, Object> wordInfo = new LinkedHashMap<>();
                    wordInfo.put("number", number);
                    wordInfo.put("word", wordName);
                    wordInfo.put("sortOrder", sortOrderNum);

                    if (foundWord != null) {
                        wordInfo.put("id", foundWord.getId());
                        if (wordCount <= 20) {
                            System.out.println("✅ 单词: " + wordName + " 序号:" + sortOrderNum + " ID:" + foundWord.getId());
                        }
                    } else {
                        wordInfo.put("id", null);
                        if (wordCount <= 20) {
                            System.out.println("⚠️ 未找到: " + wordName + " 序号:" + sortOrderNum);
                        }
                    }

                    // 添加到当前层级（最深层的节点）
                    Map<String, Object> currentParent = stack.get(stack.size() - 1);
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> words = (List<Map<String, Object>>) currentParent.get("words");
                    words.add(wordInfo);
                }
            }

            System.out.println("✅ 目录构建完成！");
            System.out.println("  标题总数: " + headingCount + " 个");
            System.out.println("  单词总数: " + wordCount + " 个");
            System.out.println("  一级分类: " + structure.size() + " 个");

            // 打印结构统计
            printStructure(structure, 0);

            return structure;
        } catch (Exception e) {
            System.err.println("❌ 构建目录失败: " + e.getMessage());
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    @SuppressWarnings("unchecked")
    private void printStructure(List<Map<String, Object>> nodes, int depth) {
        String indent = String.join("", Collections.nCopies(depth, "  "));
        for (Map<String, Object> node : nodes) {
            String title = (String) node.get("title");
            Integer level = (Integer) node.get("level");
            List<Map<String, Object>> children = (List<Map<String, Object>>) node.get("children");
            List<Map<String, Object>> words = (List<Map<String, Object>>) node.get("words");

            System.out.println(indent + "📁 " + title + " (L" + level + ") - 单词:" + words.size() + ", 子分类:" + children.size());
            if (!children.isEmpty()) {
                printStructure(children, depth + 1);
            }
        }
    }

    private String readFileMd() {
        try {
            // 尝试多个路径
            List<Path> possiblePaths = Arrays.asList(
                    Paths.get(System.getProperty("user.dir"), "src", "main", "resources", "file.md"),
                    Paths.get(System.getProperty("user.dir"), "file.md"),
                    Paths.get("src", "main", "resources", "file.md"),
                    Paths.get("file.md")
            );

            for (Path filePath : possiblePaths) {
                if (Files.exists(filePath)) {
                    System.out.println("📂 找到文件: " + filePath.toAbsolutePath());
                    String content = Files.readString(filePath, StandardCharsets.UTF_8);
                    System.out.println("📖 文件大小: " + content.length() + " 字符");
                    return content;
                }
            }

            // 从 classpath 读取
            ClassPathResource resource = new ClassPathResource("file.md");
            if (resource.exists()) {
                System.out.println("📂 从 classpath 读取 file.md");
                return new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            }

            System.err.println("❌ 无法找到 file.md 文件");
            return null;
        } catch (Exception e) {
            System.err.println("❌ 读取 file.md 失败: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }
}