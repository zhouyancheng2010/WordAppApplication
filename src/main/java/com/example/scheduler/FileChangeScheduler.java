package com.example.scheduler;

import com.example.model.Word;
import com.example.service.WordService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class FileChangeScheduler {

    @Autowired
    private WordService wordService;

    private static final String MD_FILE_PATH = "src/main/resources/file.md";
    private static long lastModifiedTime = 0;

    @Scheduled(fixedRate = 60000)
    public void checkFileChange() {
        File mdFile = new File(MD_FILE_PATH);
        if (!mdFile.exists()) {
            System.out.println("⚠️ 找不到 file.md，请检查路径");
            return;
        }

        long currentModifiedTime = mdFile.lastModified();
        if (currentModifiedTime > lastModifiedTime) {
            lastModifiedTime = currentModifiedTime;
            System.out.println("🚀 开始解析 file.md 并同步原始 Markdown 格式...");
            parseAndSave(mdFile);
        }
    }

    private void parseAndSave(File file) {
        try {
            // 读取文件内容
            String content = new String(Files.readAllBytes(Paths.get(file.getPath())), StandardCharsets.UTF_8);
            // 按 ------ 分割单词块
            String[] blocks = content.split("------");
            int count = 0;

            for (String block : blocks) {
                String trimmed = block.trim();
                if (trimmed.isEmpty()) continue;

                Word word = new Word();
                word.setContent(trimmed); // 核心：存入完整的 Markdown 原始内容

                // 提取单词作为 ID (匹配格式：001 **a** /ə/)
                Pattern p = Pattern.compile("^\\d+\\s+\\*\\*(.+?)\\*\\*\\s+\\/", Pattern.MULTILINE);
                Matcher m = p.matcher(trimmed);

                if (m.find()) {
                    word.setWord(m.group(1).trim());
                    wordService.addWord(word);
                    count++;
                }
            }
            System.out.println("✅ 同步成功！共导入 " + count + " 个单词的原始 Markdown 内容。");
        } catch (Exception e) {
            System.err.println("❌ 解析失败:");
            e.printStackTrace();
        }
    }
}
