package com.tanyi.rag.config;

import cn.hutool.crypto.SecureUtil;
import jakarta.annotation.PostConstruct;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.TextReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.data.redis.core.RedisTemplate;

import java.nio.charset.Charset;
import java.util.List;

/**
 * @auther zzyybs@126.com
 * @create 2025-07-30 12:16
 * @Description 初始化数据到 vector dataBase
 */
@Configuration
public class InitVectorDatabaseConfig {

    /**
     * 向量数据库操作对象（由 spring-ai 自动装配，底层为 Redis Stack 的向量存储实现）
     * 提供 add() 写入向量、similaritySearch() 相似度检索等能力
     */
    @Autowired
    private VectorStore vectorStore;

    /**
     * Redis 模板，用于执行原生的 Redis 命令
     * 这里主要借助其 setIfAbsent()（即 Redis 的 SETNX 命令）实现"分布式幂等判断"，防止重复初始化
     */
    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    /**
     * 待向量化的原始文本文件，从 classpath 根路径下加载 ops.txt
     * @Value 注解会在 Bean 初始化时自动注入该资源
     */
    @Value("classpath:ops.txt")
    private Resource opsFile;

    /**
     * 容器启动完成、依赖注入完毕后自动执行一次
     * 整体流程：读取文件 → 分词切块 → 写入向量库（带去重保护）
     * 说明：加 @PostConstruct 是为了让应用启动时自动完成知识库的初始化，无需手动调用
     */
    @PostConstruct
    public void init() {
        // 1 读取文件
        // TextReader 是 Spring AI 提供的文本读取器，负责把 Resource 内容读取成 List<Document>
        TextReader textReader = new TextReader(opsFile);
        // 设置字符编码为系统默认编码，避免中文乱码
        textReader.setCharset(Charset.defaultCharset());

        // 2 文件转换为向量(开启分词)
        // TokenTextSplitter 按 token 数量把长文档切成若干小片段(Chunk)
        // 切片的好处：① 单段更聚焦语义；② 便于后续按片段做相似度检索；③ 避免超出模型的上下文长度限制
        List<Document> list = new TokenTextSplitter().transform(textReader.read());

        // 3 写入向量数据库RedisStack
        // 直接把所有切片写入向量库，但这样存在一个问题：
        // 每次应用重启都会重复执行，导致同样的数据被反复写入 → 向量库中存在大量重复冗余数据
        // vectorStore.add(list);

        // 解决上面第3步，向量数据重复问题，使用redis setnx命令处理
        // 4 去重复版本
        // 取出文件的 source 元数据（通常是文件路径/资源标识），用它作为"这份文件是否已入库"的判断依据
        String sourceMetadata = (String) textReader.getCustomMetadata().get("source");
        // 对 source 做 MD5，生成固定长度的指纹，避免 key 过长且便于作为 Redis Key
        String textHash = SecureUtil.md5(sourceMetadata);
        // 拼接完整的 Redis Key 前缀，便于和其他业务数据隔离，方便查看与管理
        String redisKey = "vector-xxx:" + textHash;

        // 判断是否存入过,redisKey如果可以成功插入表示以前没有过，可以加入向量数据
        // setIfAbsent 对应 Redis 的 SETNX（SET if Not eXists）：
        //   - 返回 true  → key 之前不存在，本次插入成功（首次加载）
        //   - 返回 false → key 已存在，说明以前已经加载过
        // 借助 Redis 的原子性，即使多实例并发启动，也只有一个实例能抢到"首次写入"的权利，天然幂等
        Boolean retFlag = redisTemplate.opsForValue().setIfAbsent(redisKey, "1");
        System.out.println("****retFlag : " + retFlag);

        if (Boolean.TRUE.equals(retFlag)) {
            // 键不存在，首次插入,可以保存进向量数据库
            // 首次执行时，把切片后的所有 Document 一次性写入向量库
            // VectorStore 内部会自动调用 EmbeddingModel 把每个切片转成向量并存入 Redis
            vectorStore.add(list);
        } else {
            // 键已存在，跳过或者报错
            // 如果希望严格阻止重复执行，可以打开下面这行抛异常；若只是提示，保留日志打印即可
            // throw new RuntimeException("---重复操作");
            System.out.println("------向量初始化数据已经加载过，请不要重复操作");
        }
    }
}
