/*
package com.example.scheduler;

import com.example.model.Word;
import com.example.repository.WordRepository;
import jakarta.annotation.PostConstruct;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


@Component
public class FileChangeScheduler2 {
    @Autowired
    private WordRepository wordRepository;

    @PersistenceContext
    private EntityManager entityManager;

    private Path filePath;
    private long lastModified = 0;

    @PostConstruct
    public void init() {
        try {
            filePath = Paths.get(System.getProperty("user.dir"), "src", "main", "resources", "file.md");
            System.out.println("📂 尝试从路径加载: " + filePath.toAbsolutePath());

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
            wordRepository.deleteAll();

            try {
                entityManager.getTransaction().begin();
                @SuppressWarnings("JpaQlInspection")
                int result = entityManager.createNativeQuery("ALTER TABLE worddb.word AUTO_INCREMENT = 1").executeUpdate();
                entityManager.getTransaction().commit();
                System.out.println("🗑️ 已清空旧数据并重置 ID");
            } catch (Exception e) {
                System.out.println("⚠️ 重置 ID 失败，继续执行: " + e.getMessage());
            }

            String[] wordBlocks = content.split("\\r?\\n-{2,}\\r?\\n");
            System.out.println("📊 分割得到 " + wordBlocks.length + " 个单词块");

            int sortOrder = 0;
            int successCount = 0;
            int skipCount = 0;

            for (int i = 0; i < wordBlocks.length; i++) {
                String block = wordBlocks[i];
                if (block.trim().isEmpty()) {
                    continue;
                }

                Pattern patternWithNumber = Pattern.compile("(?:^|\\n)(\\d{3})\\s+\\*\\*(.+?)\\*\\*\\s+([^\\s]+)\\s+(.+)", Pattern.MULTILINE | Pattern.DOTALL);
                Pattern patternWithoutNumber = Pattern.compile("(?:^|\\n)\\*\\*(\\d+)\\s+(.+?)\\*\\*\\s+([^\\s]+)\\s+(.+)", Pattern.MULTILINE | Pattern.DOTALL);

                Matcher matcherWithNumber = patternWithNumber.matcher(block);
                Matcher matcherWithoutNumber = patternWithoutNumber.matcher(block);

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

                    word.setContent(block.trim());

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

                    word.setContent(block.trim());

                    matched = true;
                }

                if (matched) {
                    wordRepository.save(word);
                    successCount++;

                    if (successCount <= 3 || successCount % 500 == 0) {
                        System.out.println("  ✅ 解析 [" + successCount + "]: " + word.getWord() + " - " + word.getDefinition());
                    }
                } else {
                    skipCount++;
                    System.out.println("⚠️ 跳过块 #" + (i + 1) + "，前150字符: " +
                            block.trim().substring(0, Math.min(150, block.trim().length())));
                }
            }

            System.out.println("✅ 成功同步 " + successCount + " 个单词，跳过 " + skipCount + " 个块");

        } catch (Exception e) {
            System.err.println("❌ 解析内容失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

}
*/