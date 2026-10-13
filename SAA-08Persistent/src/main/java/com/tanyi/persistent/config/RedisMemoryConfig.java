package com.tanyi.persistent.config;

import com.alibaba.cloud.ai.memory.redis.JedisRedisChatMemoryRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * redis 存储配置类
 */
@Configuration
public class RedisMemoryConfig {

    @Value("${spring.data.redis.host}")
    private String host;

    @Value("${spring.data.redis.port}")
    private Integer port;

    @Value("${spring.data.redis.password}")
    private String password;

    /**
     * 注入 Jedis Redis 对话内存存储器
     * @return
     */
    @Bean
    public JedisRedisChatMemoryRepository jedisRedisChatMemoryRepository() {
        return JedisRedisChatMemoryRepository.builder()
                .host(host)
                .port(port)
                .password(password)
                .build();
    }
}
