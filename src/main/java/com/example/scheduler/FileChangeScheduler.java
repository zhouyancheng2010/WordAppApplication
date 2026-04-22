package com.example.scheduler;

import com.example.model.Word;
import com.example.repository.WordRepository;
import jakarta.annotation.PostConstruct;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


@Component
public class FileChangeScheduler {
    @Autowired
    private WordRepository wordRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private Path filePath;
    private long lastModified = 0;

    @PostConstruct
    public void init() {
        try {
            /*从资源库读取file.md文件
            filePath = Paths.get(System.getProperty("user.dir"), "src", "main", "resources", "file.md");
            System.out.println("📂 尝试从路径加载: " + filePath.toAbsolutePath());
            */

            //从jar包同级目录读取file.md文件
            filePath = Paths.get(System.getProperty("user.dir"), "file.md");
            System.out.println("📂 工作目录: " + System.getProperty("user.dir"));
            System.out.println("📂 file.md路径: " + filePath.toAbsolutePath());

            //以下代码不变
            if (Files.exists(filePath)) {
                System.out.println("✅ 文件存在，开始同步...");
                lastModified = Files.getLastModifiedTime(filePath).toMillis();
                parseAndSaveWords();
            } else {
                System.err.println("❌ 文件不存在: " + filePath.toAbsolutePath());
                tryLoadFromClasspath();
            }
        } catch (IOException e) {
            System.err.println("❌ 初始化文件监控失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void tryLoadFromClasspath() {
        try {
            System.out.println("📂 尝试从 classpath 加载...");
            InputStream is = getClass().getClassLoader().getResourceAsStream("file.md");
            if (is != null) {
                String content = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                System.out.println("✅ 从 classpath 加载成功，文件大小: " + content.length() + " 字符");
                parseContent(content);
            } else {
                System.err.println("❌ 无法从 classpath 找到 file.md");
            }
        } catch (Exception e) {
            System.err.println("❌ 从 classpath 加载失败: " + e.getMessage());
        }
    }

    @Scheduled(fixedRate = 5000)
    public void checkFileChange() {
        if (filePath == null || !Files.exists(filePath)) {
            return;
        }

        try {
            long currentModified = Files.getLastModifiedTime(filePath).toMillis();
            if (currentModified != lastModified) {
                System.out.println("🔄 检测到文件变化，开始同步...");
                lastModified = currentModified;
                parseAndSaveWords();
            }
        } catch (IOException e) {
            System.err.println("❌ 检查文件变化失败: " + e.getMessage());
        }
    }

    private void parseAndSaveWords() {
        try {
            String content = Files.readString(filePath, StandardCharsets.UTF_8);
            System.out.println("📖 读取文件成功，大小: " + content.length() + " 字符");
            parseContent(content);
        } catch (IOException e) {
            System.err.println("❌ 解析文件失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void parseContent(String content) {
        try {
            String[] wordBlocks = content.split("\\r?\\n-{2,}\\r?\\n");
            System.out.println("📊 分割得到 " + wordBlocks.length + " 个单词块");

            Map<Long, Word> parsedWords = new LinkedHashMap<>();
            int sortOrder = 0;
            int skipCount = 0;

            String currentLayer1 = null;
            String currentLayer2 = null;
            String currentLayer3 = null;

            Pattern layer1Pattern = Pattern.compile("^####\\s+(.+)");
            Pattern layer2Pattern = Pattern.compile("^#####\\s+(.+)");
            Pattern layer3Pattern = Pattern.compile("^######\\s+(.+)");
            Pattern layer3AltPattern = Pattern.compile("^(.+?)\\s*\\（\\d+词\\）$");
            Pattern wordPattern1 = Pattern.compile("(?:^|\\n)(\\d{3})\\s+\\*\\*(.+?)\\*\\*\\s+([^\\s]+)\\s+(.+)", Pattern.MULTILINE | Pattern.DOTALL);
            Pattern wordPattern2 = Pattern.compile("(?:^|\\n)\\*\\*(\\d{3})\\s+(.+?)\\*\\*\\s+([^\\s]+)\\s+(.+)", Pattern.MULTILINE | Pattern.DOTALL);

            for (int i = 0; i < wordBlocks.length; i++) {
                String block = wordBlocks[i];
                if (block.trim().isEmpty()) {
                    continue;
                }

                String[] blockLines = block.split("\\r?\\n");
                for (String line : blockLines) {
                    String trimmedLine = line.trim();

                    if (trimmedLine.isEmpty() || trimmedLine.startsWith("------") || trimmedLine.startsWith("---")) {
                        continue;
                    }

                    Matcher layer1Matcher = layer1Pattern.matcher(trimmedLine);
                    if (layer1Matcher.find()) {
                        currentLayer1 = layer1Matcher.group(1).trim();
                        currentLayer2 = null;
                        currentLayer3 = null;
                        continue;
                    }

                    Matcher layer2Matcher = layer2Pattern.matcher(trimmedLine);
                    if (layer2Matcher.find()) {
                        currentLayer2 = layer2Matcher.group(1).trim();
                        currentLayer3 = null;
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

                    if (layer3Text != null) {
                        currentLayer3 = layer3Text;
                        continue;
                    }
                }

                Matcher matcherWithNumber = wordPattern1.matcher(block);
                Matcher matcherWithoutNumber = wordPattern2.matcher(block);

                Word word = new Word();
                boolean matched = false;

                if (matcherWithNumber.find()) {
                    sortOrder++;
                    word.setId((long) sortOrder);
                    word.setSortOrder(sortOrder);
                    word.setWord(matcherWithNumber.group(2).trim());

                    String ipa = matcherWithNumber.group(3).trim();
                    word.setPronunciation(ipa);

                    String afterIpa = matcherWithNumber.group(4).split("\n")[0].trim();
                    word.setDefinition(afterIpa);

                    word.setLayer1(currentLayer1);
                    word.setLayer2(currentLayer2);
                    word.setLayer3(currentLayer3);

                    int matchStart = matcherWithNumber.start();
                    String wordContent = block.substring(matchStart).trim();
                    word.setContent(wordContent);

                    matched = true;
                } else if (matcherWithoutNumber.find()) {
                    sortOrder++;
                    word.setId((long) sortOrder);
                    word.setSortOrder(sortOrder);
                    word.setWord(matcherWithoutNumber.group(2).trim());

                    String ipa = matcherWithoutNumber.group(3).trim();
                    word.setPronunciation(ipa);

                    String afterIpa = matcherWithoutNumber.group(4).split("\n")[0].trim();
                    word.setDefinition(afterIpa);

                    word.setLayer1(currentLayer1);
                    word.setLayer2(currentLayer2);
                    word.setLayer3(currentLayer3);

                    int matchStart = matcherWithoutNumber.start();
                    String wordContent = block.substring(matchStart).trim();
                    word.setContent(wordContent);

                    matched = true;
                }

                if (matched) {
                    parsedWords.put(word.getId(), word);
                    if (parsedWords.size() <= 3 || parsedWords.size() % 500 == 0) {
                        System.out.println("  ✅ 解析 [" + parsedWords.size() + "]: " + word.getWord() +
                                " | L1:" + currentLayer1 + " | L2:" + currentLayer2 + " | L3:" + currentLayer3);
                    }
                } else {
                    skipCount++;
                    if (skipCount <= 5) {
                        System.out.println("⚠️ 跳过块 #" + (i + 1) + "，前150字符: " +
                                block.trim().substring(0, Math.min(150, block.trim().length())));
                    }
                }
            }

            System.out.println("📋 解析完成，有效单词: " + parsedWords.size() + ", 跳过: " + skipCount);

            syncDatabase(parsedWords);

        } catch (Exception e) {
            System.err.println("❌ 解析内容失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
    /*增量同步逻辑
    private void syncDatabase(Map<Long, Word> parsedWords) {
        List<Word> existingWords = wordRepository.findAll();
        Map<Long, Word> existingMap = new HashMap<>();
        for (Word w : existingWords) {
            existingMap.put(w.getId(), w);
        }

        List<Word> toAdd = new ArrayList<>();
        List<Word> toUpdate = new ArrayList<>();

        for (Map.Entry<Long, Word> entry : parsedWords.entrySet()) {
            Long id = entry.getKey();
            Word newWord = entry.getValue();

            if (!existingMap.containsKey(id)) {
                toAdd.add(newWord);
            } else {
                Word existingWord = existingMap.get(id);
                if (!isSameContent(existingWord, newWord)) {
                    toUpdate.add(newWord);
                }
            }
        }

        System.out.println("📊 需要同步: 新增=" + toAdd.size() + ", 更新=" + toUpdate.size());

        if (!toAdd.isEmpty() || !toUpdate.isEmpty()) {
            fullSync(parsedWords);
        } else {
            System.out.println("✅ 数据已是最新，无需同步");
        }
    }

    private boolean isSameContent(Word existing, Word newWord) {
        return Objects.equals(existing.getWord(), newWord.getWord()) &&
                Objects.equals(existing.getPronunciation(), newWord.getPronunciation()) &&
                Objects.equals(existing.getDefinition(), newWord.getDefinition());
    }

    private void fullSync(Map<Long, Word> parsedWords) {
        transactionTemplate.execute(status -> {
            try {
                System.out.println("🗑️ 清空旧数据...");
                wordRepository.deleteAll();

                List<Word> wordsToSave = new ArrayList<>(parsedWords.values());

                int batchSize = 500;
                for (int i = 0; i < wordsToSave.size(); i += batchSize) {
                    int endIndex = Math.min(i + batchSize, wordsToSave.size());
                    List<Word> batch = wordsToSave.subList(i, endIndex);
                    wordRepository.saveAll(batch);
                    entityManager.flush();
                    entityManager.clear();
                    System.out.println("  📦 已保存批次: " + (i / batchSize + 1) + " (" + batch.size() + " 条)");
                }

                System.out.println("✅ 全量同步完成，共保存 " + wordsToSave.size() + " 个单词");
                return null;
            } catch (Exception e) {
                System.err.println("❌ 全量同步失败: " + e.getMessage());
                e.printStackTrace();
                status.setRollbackOnly();
                return null;
            }
        });
    }
    */
    //全量同步逻辑
    private void syncDatabase(Map<Long, Word> parsedWords) {
        if (parsedWords.isEmpty()) {
            System.out.println("⚠️ 无数据可同步");
            return;
        }

        System.out.println("📊 开始全量同步，共 " + parsedWords.size() + " 个单词");
        fullSync(parsedWords);
    }

    private boolean isSameContent(Word existing, Word newWord) {
        return Objects.equals(existing.getWord(), newWord.getWord()) &&
                Objects.equals(existing.getPronunciation(), newWord.getPronunciation()) &&
                Objects.equals(existing.getDefinition(), newWord.getDefinition());
    }
    @SuppressWarnings({"JpaQlInspection", "SqlResolve"})
    private void fullSync(Map<Long, Word> parsedWords) {
        transactionTemplate.execute(status -> {
            try {
                System.out.println("🗑️ 清空旧数据...");
                wordRepository.deleteAll();

                entityManager.createNativeQuery("ALTER TABLE word AUTO_INCREMENT = 1").executeUpdate();
                System.out.println("🔄 重置自增ID");

                List<Word> wordsToSave = new ArrayList<>(parsedWords.values());

                int batchSize = 500;
                for (int i = 0; i < wordsToSave.size(); i += batchSize) {
                    int endIndex = Math.min(i + batchSize, wordsToSave.size());
                    List<Word> batch = wordsToSave.subList(i, endIndex);
                    wordRepository.saveAll(batch);
                    entityManager.flush();
                    entityManager.clear();
                    System.out.println("  📦 已保存批次: " + (i / batchSize + 1) + " (" + batch.size() + " 条)");
                }

                System.out.println("✅ 全量同步完成，共保存 " + wordsToSave.size() + " 个单词");
                return null;
            } catch (Exception e) {
                System.err.println("❌ 全量同步失败: " + e.getMessage());
                e.printStackTrace();
                status.setRollbackOnly();
                return null;
            }
        });
    }
    //全量同步逻辑结束
}


