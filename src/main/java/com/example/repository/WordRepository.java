package com.example.repository;

import com.example.model.Word;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface WordRepository extends JpaRepository<Word, Long> {

    Optional<Word> findByWord(String word);

    Optional<Word> findByWordAndSortOrder(String word, Integer sortOrder);

    Page<Word> findByWordContainingIgnoreCase(String word, Pageable pageable);

    Page<Word> findByContentContaining(String keyword, Pageable pageable);

    List<Word> findBySortOrderLessThanEqual(Integer sortOrder);

    Optional<Word> findByWordAndContentStartingWith(String word, String prefix);

    @Query("SELECT w FROM Word w WHERE w.layer1 = :layer1 ORDER BY w.sortOrder")
    List<Word> findByLayer1(@Param("layer1") String layer1);
}