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
                System.out.println("📝 数据库为空，等待同步 Markdown 文件...");
            } else {
                System.out.println("✅ 数据库已有 " + repository.count() + " 个单词");
            }
        };
    }
}
