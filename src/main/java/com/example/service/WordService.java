package com.example.service;

import com.example.model.Word;
import com.example.repository.WordRepository;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
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

    public byte[] exportToMarkdown() {
        try {
            StringBuilder md = new StringBuilder();
            md.append("# 英语单词手册\n\n");
            md.append("## 目录\n\n");

            List<Map<String, Object>> catalog = buildCatalogStructure();
            generateTableOfContents(md, catalog, 0);

            md.append("\n---\n\n");

            appendCatalogContent(md, catalog);

            return md.toString().getBytes(StandardCharsets.UTF_8);
        } catch (Exception e) {
            System.err.println("MD导出失败: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("导出MD失败: " + e.getMessage(), e);
        }
    }

    public byte[] exportToPdf() {
        try {
            System.out.println("开始导出PDF...");

            List<Map<String, Object>> catalog = buildCatalogStructure();

            // 生成带书签标记的 HTML
            StringBuilder html = new StringBuilder();
            html.append("<!DOCTYPE html><html lang='zh-CN'><head><meta charset='UTF-8'/><title>英语单词手册</title>");
            html.append("<style>");
            html.append("@page { size: A4; margin: 2cm; }");
            html.append("body { font-family: 'SimHei', Arial, sans-serif; padding: 0; line-height: 1.6; font-size: 12pt; color: #000; }");
            html.append("h1 { font-size: 24pt; text-align: center; margin: 20pt 0; page-break-after: always; -fs-bookmark-level: 1; }");
            html.append("h2 { font-size: 18pt; margin: 16pt 0 8pt 0; page-break-after: always; -fs-bookmark-level: 2; }");
            html.append("h3 { font-size: 16pt; margin: 14pt 0 6pt 0; -fs-bookmark-level: 3; }");
            html.append("h4 { font-size: 14pt; margin: 12pt 0 6pt 0; -fs-bookmark-level: 4; }");
            html.append("h5 { font-size: 13pt; margin: 10pt 0 4pt 0; -fs-bookmark-level: 5; }");
            html.append("h6 { font-size: 12pt; margin: 8pt 0 4pt 0; -fs-bookmark-level: 6; }");
            html.append(".toc { margin: 20pt 0; page-break-after: always; }");
            html.append(".toc-item { margin: 3pt 0; line-height: 1.4; }");
            html.append(".word-card { margin: 12pt 0; padding: 8pt 0; page-break-inside: avoid; }");
            html.append(".word-number { font-weight: bold; }");
            html.append("p { margin: 4pt 0; }");
            html.append("strong { font-weight: bold; }");
            html.append("hr { border: none; border-top: 1pt solid #ccc; margin: 12pt 0; }");
            html.append("</style></head><body>");

            // 添加标题
            html.append("<h1>英语单词手册</h1>");

            // 添加目录
            html.append("<h2>目录</h2>");
            html.append("<div class='toc'>");
            generateHtmlBookmarks(html, catalog, 0);
            html.append("</div>");

            html.append("<hr/>");

            // 添加内容（带书签标记）
            appendHtmlCatalogContentWithBookmarks(html, catalog);

            html.append("</body></html>");

            // ***** 删除以下重复的代码 *****
            // html.append("<h1 data-bookmark='英语单词手册'>英语单词手册</h1>");
            // html.append("<h2 class='toc' data-bookmark='目录'>目录</h2>");
            // html.append("<div class='toc'>");
            // generateHtmlBookmarks(html, catalog, 0);
            // html.append("</div>");
            // html.append("<hr/>");
            // appendHtmlCatalogContentWithBookmarks(html, catalog);
            // html.append("</body></html>");

            System.out.println("生成PDF文件，HTML长度: " + html.length());

            // 调试：保存 HTML 到文件以便检查
            try {
                java.nio.file.Files.write(java.nio.file.Paths.get("debug_output.html"), html.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
                System.out.println("✅ HTML 已保存到 debug_output.html");
            } catch (Exception e) {
                System.err.println("保存调试文件失败: " + e.getMessage());
            }

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

            try {
                PdfRendererBuilder builder = new PdfRendererBuilder();
                builder.withHtmlContent(html.toString(), null);
                builder.toStream(outputStream);

                // 从 classpath 加载字体文件
                try {
                    org.springframework.core.io.ClassPathResource simheiResource =
                            new org.springframework.core.io.ClassPathResource("fonts/simhei.ttf");
                    if (simheiResource.exists()) {
                        builder.useFont(new com.openhtmltopdf.extend.FSSupplier<java.io.InputStream>() {
                            @Override
                            public java.io.InputStream supply() {
                                try {
                                    return simheiResource.getInputStream();
                                } catch (java.io.IOException e) {
                                    throw new RuntimeException("Failed to load font", e);
                                }
                            }
                        }, "SimHei");
                        System.out.println("✅ 成功加载字体: fonts/simhei.ttf");
                    } else {
                        System.out.println("⚠️ 未找到字体文件");
                    }
                } catch (Exception fontEx) {
                    System.err.println("字体加载异常: " + fontEx.getMessage());
                }

                builder.useFastMode();
                builder.run();

                System.out.println("PDF导出成功，大小: " + outputStream.size() + " bytes");
                return outputStream.toByteArray();
            } catch (Exception pdfEx) {
                System.err.println("PDF渲染失败: " + pdfEx.getMessage());
                pdfEx.printStackTrace();
                throw new RuntimeException("PDF导出失败: " + pdfEx.getMessage(), pdfEx);
            }
        } catch (Exception e) {
            System.err.println("PDF导出失败: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("导出PDF失败: " + e.getMessage(), e);
        }
    }

    public byte[] exportToWord() {
        try {
            System.out.println("开始导出Word...");

            XWPFDocument document = new XWPFDocument();

            // 标题
            XWPFParagraph titlePara = document.createParagraph();
            titlePara.setAlignment(ParagraphAlignment.CENTER);
            XWPFRun titleRun = titlePara.createRun();
            titleRun.setText("英语单词手册");
            titleRun.setBold(true);
            titleRun.setFontSize(24);
            titleRun.setColor("667eea");

            // 目录标题
            XWPFParagraph tocTitlePara = document.createParagraph();
            tocTitlePara.setSpacingBefore(400);
            XWPFRun tocTitleRun = tocTitlePara.createRun();
            tocTitleRun.setText("目录");
            tocTitleRun.setBold(true);
            tocTitleRun.setFontSize(16);
            tocTitleRun.setColor("333333");

            // 插入目录域（TOC field）
            XWPFParagraph tocPara = document.createParagraph();
            XWPFRun tocRun = tocPara.createRun();
            // 添加 TOC 域代码
            String tocField = "TOC \\o \"1-6\" \\h \\z \\u";
            tocRun.getCTR().addNewFldChar().setFldCharType(org.openxmlformats.schemas.wordprocessingml.x2006.main.STFldCharType.BEGIN);
            tocRun.getCTR().addNewInstrText().setStringValue(tocField);
            tocRun.getCTR().addNewFldChar().setFldCharType(org.openxmlformats.schemas.wordprocessingml.x2006.main.STFldCharType.SEPARATE);
            tocRun.getCTR().addNewFldChar().setFldCharType(org.openxmlformats.schemas.wordprocessingml.x2006.main.STFldCharType.END);

            // 分页符
            XWPFParagraph pageBreakPara = document.createParagraph();
            pageBreakPara.setPageBreak(true);

            // 生成Markdown内容并转换为Word
            StringBuilder md = new StringBuilder();
            md.append("# 英语单词手册\n\n");
            md.append("## 目录\n\n");

            List<Map<String, Object>> catalog = buildCatalogStructure();
            generateTableOfContents(md, catalog, 0);

            md.append("\n---\n\n");
            appendCatalogContent(md, catalog);

            // 将Markdown转换为Word
            String[] lines = md.toString().split("\\r?\\n");
            boolean skipFirstLines = true;

            for (String line : lines) {
                // 跳过前面的标题和目录部分
                if (skipFirstLines) {
                    if (line.equals("---")) {
                        skipFirstLines = false;
                    }
                    continue;
                }

                if (line.trim().isEmpty()) {
                    document.createParagraph();
                    continue;
                }

                XWPFParagraph para = document.createParagraph();
                XWPFRun run = para.createRun();

                // 检测标题层级并设置大纲级别
                if (line.startsWith("###### ")) {
                    run.setText(line.substring(7));
                    run.setBold(true);
                    run.setFontSize(12);
                    run.setColor("1a202c");
                    para.setStyle("Heading6");
                } else if (line.startsWith("##### ")) {
                    run.setText(line.substring(6));
                    run.setBold(true);
                    run.setFontSize(13);
                    run.setColor("2d3748");
                    para.setStyle("Heading5");
                } else if (line.startsWith("#### ")) {
                    run.setText(line.substring(5));
                    run.setBold(true);
                    run.setFontSize(14);
                    run.setColor("4a5568");
                    para.setStyle("Heading4");
                } else if (line.startsWith("### ")) {
                    run.setText(line.substring(4));
                    run.setBold(true);
                    run.setFontSize(16);
                    run.setColor("5a67d8");
                    para.setStyle("Heading3");
                } else if (line.startsWith("## ")) {
                    run.setText(line.substring(3));
                    run.setBold(true);
                    run.setFontSize(18);
                    run.setColor("764ba2");
                    para.setStyle("Heading2");
                } else if (line.startsWith("# ")) {
                    run.setText(line.substring(2));
                    run.setBold(true);
                    run.setFontSize(22);
                    run.setColor("667eea");
                    para.setAlignment(ParagraphAlignment.CENTER);
                    para.setStyle("Heading1");
                } else if (line.equals("---") || line.equals("----")) {
                    run.setText("─────────────────────────────────────");
                    run.setColor("cccccc");
                } else {
                    // 普通文本，处理粗体
                    String text = line;
                    if (text.contains("**")) {
                        // 简单处理粗体
                        String[] parts = text.split("\\*\\*");
                        for (int i = 0; i < parts.length; i++) {
                            XWPFRun partRun = para.createRun();
                            partRun.setText(parts[i]);
                            if (i % 2 == 1) {
                                partRun.setBold(true);
                            }
                        }
                    } else {
                        run.setText(text);
                        run.setFontSize(11);
                    }
                }
            }

            System.out.println("生成Word文件...");
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            document.write(outputStream);
            document.close();

            System.out.println("Word导出成功，大小: " + outputStream.size() + " bytes");
            return outputStream.toByteArray();
        } catch (Exception e) {
            System.err.println("Word导出失败: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("导出Word失败: " + e.getMessage(), e);
        }
    }


    public byte[] exportToExcel() {
        try {
            XSSFWorkbook workbook = new XSSFWorkbook();
            Sheet sheet = workbook.createSheet("英语单词");

            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setFontHeightInPoints((short) 12);
            headerFont.setColor(IndexedColors.WHITE.getIndex());
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.GREY_50_PERCENT.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            Row headerRow = sheet.createRow(0);
            String[] headers = {"序号", "单词", "音标", "释义"};
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            sheet.setColumnWidth(0, 2000);
            sheet.setColumnWidth(1, 4000);
            sheet.setColumnWidth(2, 5000);
            sheet.setColumnWidth(3, 12000);

            List<Word> words = getAllWordsBySortOrder();
            int rowNum = 1;
            for (Word word : words) {
                Row row = sheet.createRow(rowNum++);

                String number = extractNumberFromContent(word.getContent());
                row.createCell(0).setCellValue(number != null ? number : "");
                row.createCell(1).setCellValue(word.getWord());
                row.createCell(2).setCellValue(word.getPronunciation() != null ? word.getPronunciation() : "");
                row.createCell(3).setCellValue(word.getDefinition() != null ? word.getDefinition() : "");
            }

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            workbook.write(outputStream);
            workbook.close();

            return outputStream.toByteArray();
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("导出Excel失败: " + e.getMessage());
        }
    }

    private void generateTableOfContents(StringBuilder md, List<Map<String, Object>> nodes, int level) {
        for (Map<String, Object> node : nodes) {
            String title = (String) node.get("title");
            if (title != null && !title.equals("root")) {
                String indent = "  ".repeat(level);
                md.append(String.format("%s- %s\n", indent, title));
            }

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> children = (List<Map<String, Object>>) node.get("children");
            if (children != null && !children.isEmpty()) {
                generateTableOfContents(md, children, level + 1);
            }

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> words = (List<Map<String, Object>>) node.get("words");
            if (words != null && !words.isEmpty()) {
                for (Map<String, Object> word : words) {
                    String wordName = (String) word.get("word");
                    String wordNumber = (String) word.get("number");
                    if (wordName != null) {
                        String indent = "  ".repeat(level + 1);
                        if (wordNumber != null) {
                            md.append(String.format("%s- %s %s\n", indent, wordNumber, wordName));
                        } else {
                            md.append(String.format("%s- %s\n", indent, wordName));
                        }
                    }
                }
            }
        }
    }

    private void generateHtmlTableOfContents(StringBuilder html, List<Map<String, Object>> nodes, int level) {
        for (Map<String, Object> node : nodes) {
            String title = (String) node.get("title");
            if (title != null && !title.equals("root")) {
                String marginLeft = (level * 20) + "px";
                html.append(String.format("<div class='toc-item' style='margin-left: %s;'>%s</div>\n",
                        marginLeft, escapeHtmlForPdf(title)));
            }

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> children = (List<Map<String, Object>>) node.get("children");
            if (children != null && !children.isEmpty()) {
                generateHtmlTableOfContents(html, children, level + 1);
            }

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> words = (List<Map<String, Object>>) node.get("words");
            if (words != null && !words.isEmpty()) {
                for (Map<String, Object> word : words) {
                    String wordName = (String) word.get("word");
                    String wordNumber = (String) word.get("number");
                    if (wordName != null) {
                        String marginLeft = ((level + 1) * 20) + "px";
                        if (wordNumber != null) {
                            html.append(String.format("<div class='toc-item' style='margin-left: %s;'>%s %s</div>\n",
                                    marginLeft, wordNumber, escapeHtmlForPdf(wordName)));
                        } else {
                            html.append(String.format("<div class='toc-item' style='margin-left: %s;'>%s</div>\n",
                                    marginLeft, escapeHtmlForPdf(wordName)));
                        }
                    }
                }
            }
        }
    }

    private void generateWordTableOfContents(XWPFDocument document, List<Map<String, Object>> nodes, int level) {
        for (Map<String, Object> node : nodes) {
            String title = (String) node.get("title");
            if (title != null && !title.equals("root")) {
                XWPFParagraph para = document.createParagraph();
                para.setIndentationLeft(level * 400);
                XWPFRun run = para.createRun();
                run.setText(title);
                run.setFontSize(12);
            }

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> children = (List<Map<String, Object>>) node.get("children");
            if (children != null && !children.isEmpty()) {
                generateWordTableOfContents(document, children, level + 1);
            }

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> words = (List<Map<String, Object>>) node.get("words");
            if (words != null && !words.isEmpty()) {
                for (Map<String, Object> word : words) {
                    String wordName = (String) word.get("word");
                    String wordNumber = (String) word.get("number");
                    if (wordName != null) {
                        XWPFParagraph para = document.createParagraph();
                        para.setIndentationLeft((level + 1) * 400);
                        XWPFRun run = para.createRun();
                        if (wordNumber != null) {
                            run.setText(wordNumber + " " + wordName);
                        } else {
                            run.setText(wordName);
                        }
                        run.setFontSize(11);
                    }
                }
            }
        }
    }

    private String extractNumberFromContent(String content) {
        if (content == null || content.isEmpty()) {
            return null;
        }
        Pattern pattern = Pattern.compile("^(\\d{3})");
        Matcher matcher = pattern.matcher(content.trim());
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    private String escapeHtmlForPdf(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private String convertMarkdownToHtml(String markdown) {
        if (markdown == null || markdown.isEmpty()) {
            return "";
        }

        // 先进行 HTML 转义
        String html = escapeHtmlForPdf(markdown);

        // 处理粗体
        html = html.replaceAll("\\*\\*(.+?)\\*\\*", "<strong>$1</strong>");

        // 处理斜体
        html = html.replaceAll("\\*(.+?)\\*", "<em>$1</em>");

        // 处理标题（从大到小）
        html = html.replaceAll("^######\\s+(.+)$", "<h6>$1</h6>");
        html = html.replaceAll("^#####\\s+(.+)$", "<h5>$1</h5>");
        html = html.replaceAll("^####\\s+(.+)$", "<h4>$1</h4>");
        html = html.replaceAll("^###\\s+(.+)$", "<h3>$1</h3>");
        html = html.replaceAll("^##\\s+(.+)$", "<h2>$1</h2>");
        html = html.replaceAll("^#\\s+(.+)$", "<h1>$1</h1>");

        // 处理代码
        html = html.replaceAll("`([^`]+)`", "<code>$1</code>");

        // 处理引用
        html = html.replaceAll("^&gt;\\s+(.+)$", "<blockquote>$1</blockquote>");

        // 处理换行
        html = html.replace("\n", "<br/>\n");

        return html;
    }

    private Map<String, Word> loadAllWordsMap() {
        Map<String, Word> wordMap = new HashMap<>();
        List<Word> allWords = getAllWordsBySortOrder();
        for (Word word : allWords) {
            wordMap.put(word.getWord().toLowerCase(), word);
        }
        return wordMap;
    }

    private Word findWordByName(String wordName) {
        List<Word> allWords = getAllWordsBySortOrder();
        for (Word word : allWords) {
            if (word.getWord().equalsIgnoreCase(wordName)) {
                return word;
            }
        }
        return null;
    }

    private void appendCatalogContent(StringBuilder md, List<Map<String, Object>> nodes) {
        for (Map<String, Object> node : nodes) {
            String title = (String) node.get("title");
            Integer level = (Integer) node.get("level");

            if (title != null && !title.equals("root") && level != null) {
                int headingLevel = Math.min(level + 1, 6);
                String hashes = "#".repeat(headingLevel);
                md.append(hashes).append(" ").append(title).append("\n\n");
            }

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> children = (List<Map<String, Object>>) node.get("children");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> words = (List<Map<String, Object>>) node.get("words");

            if (children != null && !children.isEmpty()) {
                // 有子节点，递归处理子节点
                appendCatalogContent(md, children);
            }

            // 单词只在叶子节点输出（没有子节点的节点）
            if ((children == null || children.isEmpty()) && words != null && !words.isEmpty()) {
                for (Map<String, Object> wordInfo : words) {
                    String wordName = (String) wordInfo.get("word");
                    String wordNumber = (String) wordInfo.get("number");
                    Long wordId = (Long) wordInfo.get("id");

                    if (wordName != null) {
                        Word word = null;
                        if (wordId != null) {
                            Optional<Word> optionalWord = getWordById(wordId);
                            if (optionalWord.isPresent()) {
                                word = optionalWord.get();
                            }
                        }

                        if (word == null) {
                            word = findWordByName(wordName);
                        }

                        if (word != null) {
                            if (wordNumber != null) {
                                md.append(String.format("###### %s %s\n\n", wordNumber, word.getWord()));
                            } else {
                                md.append(String.format("###### %s\n\n", word.getWord()));
                            }

                            if (word.getPronunciation() != null && !word.getPronunciation().isEmpty()) {
                                md.append(String.format("**音标**: %s\n\n", word.getPronunciation()));
                            }

                            if (word.getDefinition() != null && !word.getDefinition().isEmpty()) {
                                md.append(String.format("**释义**: %s\n\n", word.getDefinition()));
                            }

                            if (word.getContent() != null && !word.getContent().isEmpty()) {
                                md.append(word.getContent()).append("\n\n");
                            }

                            md.append("---\n\n");
                        }
                    }
                }
            }
        }
    }

    private void appendHtmlCatalogContent(StringBuilder html, List<Map<String, Object>> nodes) {
        for (Map<String, Object> node : nodes) {
            String title = (String) node.get("title");
            Integer level = (Integer) node.get("level");

            if (title != null && !title.equals("root") && level != null) {
                int headingLevel = Math.min(level + 1, 6);
                html.append(String.format("<h%d>%s</h%d>\n", headingLevel, escapeHtmlForPdf(title), headingLevel));
            }

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> children = (List<Map<String, Object>>) node.get("children");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> words = (List<Map<String, Object>>) node.get("words");

            if (children != null && !children.isEmpty()) {
                // 有子节点，递归处理子节点
                appendHtmlCatalogContent(html, children);
            }

            // 单词只在叶子节点输出（没有子节点的节点）
            if ((children == null || children.isEmpty()) && words != null && !words.isEmpty()) {
                for (Map<String, Object> wordInfo : words) {
                    String wordName = (String) wordInfo.get("word");
                    String wordNumber = (String) wordInfo.get("number");
                    Long wordId = (Long) wordInfo.get("id");

                    if (wordName != null) {
                        Word word = null;
                        if (wordId != null) {
                            Optional<Word> optionalWord = getWordById(wordId);
                            if (optionalWord.isPresent()) {
                                word = optionalWord.get();
                            }
                        }

                        if (word == null) {
                            word = findWordByName(wordName);
                        }

                        if (word != null) {
                            html.append("<div class='word-card'>\n");

                            if (wordNumber != null) {
                                html.append(String.format("<h6><span class='word-number'>%s</span> %s</h6>\n",
                                        wordNumber, escapeHtmlForPdf(word.getWord())));
                            } else {
                                html.append(String.format("<h6>%s</h6>\n",
                                        escapeHtmlForPdf(word.getWord())));
                            }

                            if (word.getPronunciation() != null && !word.getPronunciation().isEmpty()) {
                                html.append(String.format("<p><strong>音标:</strong> %s</p>\n",
                                        escapeHtmlForPdf(word.getPronunciation())));
                            }

                            if (word.getDefinition() != null && !word.getDefinition().isEmpty()) {
                                html.append(String.format("<p><strong>释义:</strong> %s</p>\n",
                                        escapeHtmlForPdf(word.getDefinition())));
                            }

                            if (word.getContent() != null && !word.getContent().isEmpty()) {
                                String contentHtml = convertMarkdownToHtml(word.getContent());
                                html.append(contentHtml).append("\n");
                            }

                            html.append("</div>\n");
                        }
                    }
                }
            }
        }
    }

    private void appendWordCatalogContent(XWPFDocument document, List<Map<String, Object>> nodes) {
        for (Map<String, Object> node : nodes) {
            String title = (String) node.get("title");
            Integer level = (Integer) node.get("level");

            if (title != null && !title.equals("root") && level != null) {
                XWPFParagraph para = document.createParagraph();
                para.setSpacingBefore(300);
                XWPFRun run = para.createRun();
                run.setText(title);
                run.setBold(true);
                run.setFontSize(18 - level);
                run.setColor("667eea");
            }

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> children = (List<Map<String, Object>>) node.get("children");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> words = (List<Map<String, Object>>) node.get("words");

            if (children != null && !children.isEmpty()) {
                // 有子节点，递归处理子节点
                appendWordCatalogContent(document, children);
            }

            // 单词只在叶子节点输出（没有子节点的节点）
            if ((children == null || children.isEmpty()) && words != null && !words.isEmpty()) {
                for (Map<String, Object> wordInfo : words) {
                    String wordName = (String) wordInfo.get("word");
                    String wordNumber = (String) wordInfo.get("number");
                    Long wordId = (Long) wordInfo.get("id");

                    if (wordName != null) {
                        Word word = null;
                        if (wordId != null) {
                            Optional<Word> optionalWord = getWordById(wordId);
                            if (optionalWord.isPresent()) {
                                word = optionalWord.get();
                            }
                        }

                        if (word == null) {
                            word = findWordByName(wordName);
                        }

                        if (word != null) {
                            XWPFParagraph wordPara = document.createParagraph();
                            wordPara.setSpacingBefore(200);

                            XWPFRun wordRun = wordPara.createRun();
                            if (wordNumber != null) {
                                wordRun.setText(wordNumber + " ");
                                wordRun.setColor("667eea");
                                wordRun.setBold(true);
                            }
                            wordRun.setText(word.getWord());
                            wordRun.setFontSize(14);
                            wordRun.setBold(true);
                            wordRun.setColor("667eea");

                            if (word.getPronunciation() != null && !word.getPronunciation().isEmpty()) {
                                XWPFParagraph pronPara = document.createParagraph();
                                XWPFRun pronRun = pronPara.createRun();
                                pronRun.setText("音标: " + word.getPronunciation());
                                pronRun.setFontSize(11);
                                pronRun.setColor("666666");
                            }

                            if (word.getDefinition() != null && !word.getDefinition().isEmpty()) {
                                XWPFParagraph defPara = document.createParagraph();
                                XWPFRun defRun = defPara.createRun();
                                defRun.setText("释义: " + word.getDefinition());
                                defRun.setFontSize(11);
                                defRun.setColor("666666");
                            }

                            if (word.getContent() != null && !word.getContent().isEmpty()) {
                                String[] lines = word.getContent().split("\\r?\\n");
                                for (String line : lines) {
                                    if (!line.trim().isEmpty()) {
                                        XWPFParagraph contentPara = document.createParagraph();
                                        XWPFRun contentRun = contentPara.createRun();
                                        contentRun.setText(line);
                                        contentRun.setFontSize(11);
                                    }
                                }
                            }

                            XWPFParagraph separator = document.createParagraph();
                            separator.setSpacingBefore(100);
                            XWPFRun sepRun = separator.createRun();
                            sepRun.setText("─────────────────────────────────────");
                            sepRun.setColor("cccccc");
                        }
                    }
                }
            }
        }
    }

    private void generateHtmlBookmarks(StringBuilder html, List<Map<String, Object>> nodes, int level) {
        for (Map<String, Object> node : nodes) {
            String title = (String) node.get("title");
            if (title != null && !title.equals("root")) {
                String marginLeft = (level * 20) + "px";
                html.append(String.format("<div class='toc-item' style='margin-left: %s;'>%s</div>\n",
                        marginLeft, escapeHtmlForPdf(title)));
            }

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> children = (List<Map<String, Object>>) node.get("children");
            if (children != null && !children.isEmpty()) {
                generateHtmlBookmarks(html, children, level + 1);
            }

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> words = (List<Map<String, Object>>) node.get("words");
            if (words != null && !words.isEmpty()) {
                for (Map<String, Object> word : words) {
                    String wordName = (String) word.get("word");
                    String wordNumber = (String) word.get("number");
                    if (wordName != null) {
                        String marginLeft = ((level + 1) * 20) + "px";
                        if (wordNumber != null) {
                            html.append(String.format("<div class='toc-item' style='margin-left: %s;'>%s %s</div>\n",
                                    marginLeft, wordNumber, escapeHtmlForPdf(wordName)));
                        } else {
                            html.append(String.format("<div class='toc-item' style='margin-left: %s;'>%s</div>\n",
                                    marginLeft, escapeHtmlForPdf(wordName)));
                        }
                    }
                }
            }
        }
    }

    private void appendHtmlCatalogContentWithBookmarks(StringBuilder html, List<Map<String, Object>> nodes) {
        for (Map<String, Object> node : nodes) {
            String title = (String) node.get("title");
            Integer level = (Integer) node.get("level");

            if (title != null && !title.equals("root") && level != null) {
                int headingLevel = Math.min(level + 1, 6);
                html.append(String.format("<h%d data-bookmark='%s'>%s</h%d>\n",
                        headingLevel, escapeHtmlForPdf(title), escapeHtmlForPdf(title), headingLevel));
            }

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> children = (List<Map<String, Object>>) node.get("children");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> words = (List<Map<String, Object>>) node.get("words");

            if (children != null && !children.isEmpty()) {
                // 有子节点，递归处理子节点
                appendHtmlCatalogContentWithBookmarks(html, children);
            }

            // 单词只在叶子节点输出（没有子节点的节点）
            if ((children == null || children.isEmpty()) && words != null && !words.isEmpty()) {
                for (Map<String, Object> wordInfo : words) {
                    String wordName = (String) wordInfo.get("word");
                    String wordNumber = (String) wordInfo.get("number");
                    Long wordId = (Long) wordInfo.get("id");

                    if (wordName != null) {
                        Word word = null;
                        if (wordId != null) {
                            Optional<Word> optionalWord = getWordById(wordId);
                            if (optionalWord.isPresent()) {
                                word = optionalWord.get();
                            }
                        }

                        if (word == null) {
                            word = findWordByName(wordName);
                        }

                        if (word != null) {
                            html.append("<div class='word-card'>\n");

                            if (wordNumber != null) {
                                html.append(String.format("<h6 data-bookmark='%s %s'><span class='word-number'>%s</span> %s</h6>\n",
                                        wordNumber, escapeHtmlForPdf(word.getWord()), wordNumber, escapeHtmlForPdf(word.getWord())));
                            } else {
                                html.append(String.format("<h6 data-bookmark='%s'>%s</h6>\n",
                                        escapeHtmlForPdf(word.getWord()), escapeHtmlForPdf(word.getWord())));
                            }

                            if (word.getPronunciation() != null && !word.getPronunciation().isEmpty()) {
                                html.append(String.format("<p><strong>音标:</strong> %s</p>\n",
                                        escapeHtmlForPdf(word.getPronunciation())));
                            }

                            if (word.getDefinition() != null && !word.getDefinition().isEmpty()) {
                                html.append(String.format("<p><strong>释义:</strong> %s</p>\n",
                                        escapeHtmlForPdf(word.getDefinition())));
                            }

                            if (word.getContent() != null && !word.getContent().isEmpty()) {
                                // 对内容进行 HTML 转义，防止特殊字符破坏 XML 结构
                                String escapedContent = escapeHtmlForPdf(word.getContent());
                                // 将 Markdown 换行转换为 HTML 换行
                                escapedContent = escapedContent.replace("\n", "<br/>\n");
                                // 处理粗体
                                escapedContent = escapedContent.replaceAll("\\*\\*(.+?)\\*\\*", "<strong>$1</strong>");
                                html.append(escapedContent).append("\n");
                            }

                            html.append("</div>\n");
                        }
                    }
                }
            }
        }
    }
}