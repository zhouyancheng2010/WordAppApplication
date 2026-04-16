package com.example.scheduler;

import com.example.model.Word;
import com.example.repository.WordRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class FileChangeScheduler {

    @Autowired
    private WordRepository wordRepository;

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
            System.out.println("🗑️ 已清空旧数据");

            // 按 ------ 分割每个单词块
            String[] wordBlocks = content.split("\n------\n");
            System.out.println("📊 分割得到 " + wordBlocks.length + " 个单词块");

            int sortOrder = 0;
            int successCount = 0;

            for (String block : wordBlocks) {
                if (block.trim().isEmpty()) {
                    continue;
                }

                Word word = new Word();

                // 提取单词序号和单词名：匹配 "001 **a** /ə/ art. 一（个）"
                Pattern pattern = Pattern.compile("^(\\d{3})\\s+\\*\\*(.+?)\\*\\*\\s+([^\\s]+)\\s+(.+)");
                Matcher matcher = pattern.matcher(block.trim());

                if (matcher.find()) {
                    sortOrder++;
                    word.setSortOrder(sortOrder);
                    word.setWord(matcher.group(2).trim());

                    // 提取音标
                    String ipa = matcher.group(3).trim();
                    word.setPronunciation(ipa);

                    // 提取释义
                    String afterIpa = matcher.group(4);
                    word.setDefinition(afterIpa.trim());

                    // 保留完整的 Markdown 内容
                    word.setContent(block.trim());

                    wordRepository.save(word);
                    successCount++;

                    if (successCount <= 3) {
                        System.out.println("  ✅ 解析: " + word.getWord() + " - " + word.getDefinition());
                    }
                } else {
                    // 如果没有匹配到，打印前100个字符用于调试
                    if (successCount == 0) {
                        System.out.println("⚠️ 未匹配到模式，块内容前100字符: " +
                                block.trim().substring(0, Math.min(100, block.trim().length())));
                    }
                }
            }

            System.out.println("✅ 成功同步 " + wordRepository.count() + " 个单词");

        } catch (Exception e) {
            System.err.println("❌ 解析内容失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
