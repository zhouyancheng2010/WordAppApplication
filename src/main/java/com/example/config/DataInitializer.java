package com.example.config;

import com.example.model.Word;
import com.example.repository.WordRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DataInitializer {

    @Bean
    CommandLineRunner initDatabase(WordRepository repository) {
        return args -> {
            if (repository.count() == 0) {
                Word word1 = new Word();
                word1.setWord("hello");
                word1.setPronunciation("/həˈloʊ/");
                word1.setDefinition("used as a greeting or to begin a conversation");
                word1.setSynonyms("hi, greetings");
                word1.setAntonyms("goodbye");
                word1.setExample("Hello, how are you?");
                word1.setRoot("hell");
                word1.setSuffix("o");
                repository.save(word1);

                Word word2 = new Word();
                word2.setWord("happy");
                word2.setPronunciation("/ˈhæpi/");
                word2.setDefinition("feeling or showing pleasure or contentment");
                word2.setSynonyms("joyful, cheerful");
                word2.setAntonyms("sad, unhappy");
                word2.setExample("She is happy with her job.");
                word2.setRoot("hap");
                word2.setSuffix("py");
                repository.save(word2);

                System.out.println("✅ Initial data inserted successfully!");
            }
        };
    }
}
