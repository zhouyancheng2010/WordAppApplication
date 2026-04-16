package com.example.service;

import com.example.model.Word;
import com.example.repository.WordRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class WordService {

    @Autowired
    private WordRepository wordRepository;

    public Page<Word> getAllWords(Pageable pageable) {
        return wordRepository.findAll(pageable);
    }

    public Word getWord(String word) {
        return wordRepository.findById(word).orElse(null);
    }

    public void addWord(Word word) {
        wordRepository.save(word);
    }
}
