package com.example.controller;

import com.example.model.Word;
import com.example.service.WordService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class WordController {

    @Autowired
    private WordService wordService;

    @GetMapping("/words")
    public Page<Word> getAllWords(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("sortOrder").ascending());
        return wordService.getAllWords(pageable);
    }

    @GetMapping("/word")
    public ResponseEntity<Word> getWord(@RequestParam String word) {
        Word result = wordService.getWord(word);
        return result != null ? ResponseEntity.ok(result) : ResponseEntity.notFound().build();
    }

    @GetMapping("/word/id/{id}")
    public ResponseEntity<Word> getWordById(@PathVariable Long id) {
        Optional<Word> word = wordService.getWordById(id);
        return word.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/word/exact")
    public ResponseEntity<Word> getWordByWordAndSortOrder(
            @RequestParam String word,
            @RequestParam Integer sortOrder) {
        Optional<Word> result = wordService.getWordByWordAndSortOrder(word, sortOrder);
        return result.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/search")
    public Page<Word> searchWords(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("sortOrder").ascending());
        return wordService.searchWords(keyword, pageable);
    }

    @GetMapping("/all-words")
    public List<Word> getAllWordsList() {
        return wordService.getAllWordsBySortOrder();
    }

    @GetMapping("/catalog")
    public List<Map<String, Object>> getCatalogStructure() {
        return wordService.buildCatalogStructure();
    }

    @GetMapping("/export/md")
    public ResponseEntity<byte[]> exportMarkdown() {
        byte[] mdContent = wordService.exportToMarkdown();
        return ResponseEntity.ok()
                .header("Content-Type", "application/octet-stream")
                .header("Content-Disposition", "attachment; filename*=UTF-8''%E8%8B%B1%E8%AF%AD%E5%8D%95%E8%AF%8D.md")
                .body(mdContent);
    }

    @GetMapping("/export/pdf")
    public ResponseEntity<byte[]> exportPdf() {
        byte[] pdfContent = wordService.exportToPdf();
        return ResponseEntity.ok()
                .header("Content-Type", "application/octet-stream")
                .header("Content-Disposition", "attachment; filename*=UTF-8''%E8%8B%B1%E8%AF%AD%E5%8D%95%E8%AF%8D.pdf")
                .body(pdfContent);
    }

    @GetMapping("/export/word")
    public ResponseEntity<byte[]> exportWord() {
        byte[] wordContent = wordService.exportToWord();
        return ResponseEntity.ok()
                .header("Content-Type", "application/octet-stream")
                .header("Content-Disposition", "attachment; filename*=UTF-8''%E8%8B%B1%E8%AF%AD%E5%8D%95%E8%AF%8D.docx")
                .body(wordContent);
    }

    @GetMapping("/export/excel")
    public ResponseEntity<byte[]> exportExcel() {
        byte[] excelContent = wordService.exportToExcel();
        return ResponseEntity.ok()
                .header("Content-Type", "application/octet-stream")
                .header("Content-Disposition", "attachment; filename*=UTF-8''%E8%8B%B1%E8%AF%AD%E5%8D%95%E8%AF%8D.xlsx")
                .body(excelContent);
    }

}
