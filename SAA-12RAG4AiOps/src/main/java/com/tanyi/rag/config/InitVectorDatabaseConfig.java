package com.tanyi.rag.config;

import jakarta.annotation.PostConstruct;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.TextReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

import java.nio.charset.Charset;
import java.util.List;

/**
 * @auther zzyybs@126.com
 * @create 2025-07-30 12:16
 * @Description 初始化数据到 vector dataBase
 */
@Configuration
public class InitVectorDatabaseConfig {

    @Autowired
    private VectorStore vectorStore;

    @Value("classpath:ops.txt")
    private Resource sqlFile;

    @PostConstruct
    public void init() {
        // 1.读取文件
        TextReader textReader = new TextReader(sqlFile);
        textReader.setCharset(Charset.defaultCharset());

        // 2.文件转换成向量（分词）
        List<Document> list = new TokenTextSplitter().transform(textReader.read());

        // 3.写入向量数据库（Redis），无法去重复版
        vectorStore.add(list);
    }
}
