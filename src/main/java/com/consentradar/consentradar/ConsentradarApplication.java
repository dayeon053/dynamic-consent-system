package com.consentradar.consentradar;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import com.consentradar.consentradar.crawler.CrawlTarget;
import com.consentradar.consentradar.crawler.CrawledPolicyDto;
import com.consentradar.consentradar.crawler.PolicyCrawler;
import org.springframework.context.ApplicationContext;

@SpringBootApplication
public class ConsentradarApplication {
    public static void main(String[] args) {
        SpringApplication.run(ConsentradarApplication.class, args);
    }
}