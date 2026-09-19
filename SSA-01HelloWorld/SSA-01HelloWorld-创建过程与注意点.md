# SSA-01HelloWorld 子模块创建过程与注意点

> 整理日期：2026-09-19。根据当前项目源码整理为可复现的学习步骤，并非 IDE 操作历史记录。这里的“子类”指 Maven 子模块，不是 Java 的类继承。本次只新增文档，不修改现有业务代码与配置。

## 1. 学习目标与模块关系

本模块使用 Spring Boot、Spring AI Alibaba 和 DashScope，提供两个 HTTP 接口：

| 接口 | 功能 | Java 返回类型 |
| --- | --- | --- |
| `/doChat?msg=你好` | 等待模型生成后，一次性返回完整回答 | `Mono<String>` |
| `/doStream?msg=你好` | 返回模型产生的文本片段 | `Flux<String>` |

请求的大致路径：

```text
浏览器 / curl
    → HelloWorldController
    → ChatModel（接口）
    → DashScopeChatModel（实现）
    → DashScopeApi
    → 百炼模型服务
```

父工程管理公共配置和依赖版本，子模块放启动类、模型配置和接口代码。

```text
SpringAiAlibaba/
├── pom.xml                         # 父工程，packaging=pom
└── SSA-01HelloWorld/
    ├── pom.xml                     # 子模块，默认打包类型为 jar
    └── src/main/
        ├── java/tanyi/saa/hellowrld/
        │   ├── SpringHelloWorldApplication.java
        │   ├── config/SaaConfig.java
        │   └── controller/HelloWorldController.java
        └── resources/application.yaml
```

> **注意：** 现有包名是 `hellowrld`。新建类时先与现有包名保持一致；若要改为 `helloworld`，需要同步修改目录、package 声明和启动配置。

## 2. 创建 Maven 子模块

在 IDEA 中打开父工程，在父工程目录下新建 Maven Module，名称填写 `SSA-01HelloWorld`，选择 JDK 21。确认子模块的父工程坐标为：

```text
com.tanyi:SpringAiAlibaba:1.0-SNAPSHOT
```

父工程 `pom.xml` 注册模块：

```xml
<packaging>pom</packaging>
<modules>
    <module>SSA-01HelloWorld</module>
</modules>
```

子模块通过 `<parent>` 继承父工程。`<modules>` 用于聚合构建，`<parent>` 用于继承配置；需要分别配置，不能相互替代。

> **注意：** 不要重复创建已经存在的模块。本文用于理解和复现创建过程；当前项目已完成这些步骤。

## 3. 配置子模块依赖

当前子模块的完整 POM 如下：

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>com.tanyi</groupId>
        <artifactId>SpringAiAlibaba</artifactId>
        <version>1.0-SNAPSHOT</version>
    </parent>

    <artifactId>SSA-01HelloWorld</artifactId>

    <properties>
        <maven.compiler.source>21</maven.compiler.source>
        <maven.compiler.target>21</maven.compiler.target>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>

    <dependencies>
        <!-- 提供 WebFlux 和内嵌 Netty，版本与当前 AI starter 引入的 Spring Boot 保持一致。 -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-webflux</artifactId>
            <version>3.5.8</version>
        </dependency>


        <dependency>
            <groupId>com.alibaba.cloud.ai</groupId>
            <artifactId>spring-ai-alibaba-agent-framework</artifactId>
        </dependency>
        <dependency>
            <groupId>com.alibaba.cloud.ai</groupId>
            <artifactId>spring-ai-alibaba-starter-dashscope</artifactId>
        </dependency>
    </dependencies>

</project>
```

依赖作用：

| 依赖 | 作用 | 注意点 |
| --- | --- | --- |
| `spring-boot-starter-webflux` | 提供响应式 Web 框架和内嵌 Netty | 当前显式指定 `3.5.8`，升级时需检查整体依赖版本 |
| `spring-ai-alibaba-agent-framework` | 提供 Agent 框架能力 | 当前 Controller 直接调用 ChatModel，尚未使用 Agent 编排 |
| `spring-ai-alibaba-starter-dashscope` | 引入 DashScope 模型支持及自动配置 | API Key、服务地址和模型权限要相互匹配 |

当前父 POM 在 `dependencyManagement` 中导入了这些 BOM：

| BOM | 当前声明版本 |
| --- | --- |
| `spring-ai-alibaba-bom` | `1.1.2.0` |
| `spring-ai-bom` | `1.1.2` |
| `spring-ai-alibaba-extensions-bom` | `1.1.2.1` |

BOM 用于管理依赖版本，不会因为导入 BOM 就自动把全部组件加到子模块中；实际使用的组件仍需放在子模块的 `<dependencies>` 中。参见 [Maven 依赖管理文档](https://maven.apache.org/guides/introduction/introduction-to-dependency-mechanism.html)。

> **注意：** 父 POM 已配置 Java 21 和 UTF-8，子模块当前重复声明了相同属性。理解继承关系后可按需简化，但本文没有修改这些配置。多个 BOM 管理同一个依赖时，也不能仅凭声明版本判断最终版本，应查看 effective POM 和依赖树。

### 为什么必须引入 WebFlux starter？

AI 相关依赖可能间接引入 `spring-webflux`，但这不等于已经引入完整的 Web 服务器。缺少服务器时，启动可能报：

```text
no org.springframework.boot.web.reactive.server.ReactiveWebServerFactory bean defined
```

本项目通过 `spring-boot-starter-webflux` 引入内嵌 Netty。无需自己编写 `ReactiveWebServerFactory` Bean，也不应通过关闭 Web 模式来绕过错误，因为当前模块需要提供 HTTP 接口。参见 [Spring Boot 内嵌服务器文档](https://docs.spring.io/spring-boot/3.5/how-to/webserver.html)。

## 4. 创建启动类

位置：`src/main/java/tanyi/saa/hellowrld/SpringHelloWorldApplication.java`。

```java
package tanyi.saa.hellowrld;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Description 启动类
 * Author
 * Date 2026/9/18
 * Version 1.0
 **/
@SpringBootApplication
public class SpringHelloWorldApplication {

    public static void main(String[] args) {
        SpringApplication.run(SpringHelloWorldApplication.class, args);
    }
}
```

`@SpringBootApplication` 启用 Spring Boot 自动配置和组件扫描。启动类位于 `tanyi.saa.hellowrld` 包下，`config` 和 `controller` 放在其子包中，可被默认扫描。

> **注意：** IDEA 的 Project SDK、Maven Runner JRE 和应用运行配置 JRE 都应与项目的 Java 21 编译配置一致。终端 `java -version` 与 IDEA 使用的 JDK 可能不同，Maven 实际使用哪个 JDK 要看 `mvn -v`。

## 5. 配置端口与模型连接信息

在 `src/main/resources/application.yaml` 中配置。以下是用于学习的环境变量模板，**不是当前文件的原样复制**，不包含真实密钥或专属工作空间地址：

```yaml
server:
  port: 8081

spring:
  ai:
    dashscope:
      api-key: ${AI_DASHSCOPE_API_KEY}
      base-url: ${AI_DASHSCOPE_BASE_URL}
      chat:
        options:
          model: ${AI_DASHSCOPE_MODEL}
```

| 配置 | 用途 | 填写要求 |
| --- | --- | --- |
| `server.port` | 本地 HTTP 端口 | 当前项目为 `8081` |
| `api-key` | 模型服务鉴权 | 从运行进程可见的环境变量读取 |
| `base-url` | DashScope 服务地址 | 使用控制台对应服务的完整 HTTPS 基地址 |
| `chat.options.model` | 期望调用的模型标识 | 使用当前工作空间有权限调用的模型 ID |

当前项目 YAML 中的模型值是 `qwen3.7-plus`；这只是源码中的配置值，不代表本文已验证该模型在你的工作空间中可用。

> **注意：** 原项目仅使用 API Key 环境变量时，不需要额外设置另两个变量。只有采用上面的完整模板时，才需要同时设置 `AI_DASHSCOPE_BASE_URL` 和 `AI_DASHSCOPE_MODEL`。

### 5.1 在 IDEA 中设置环境变量

打开 **Run → Edit Configurations**，选择 `SpringHelloWorldApplication`。在 **Environment variables** 中设置，例如：

```text
AI_DASHSCOPE_API_KEY=替换为有效密钥
AI_DASHSCOPE_BASE_URL=https://替换为控制台提供的服务域名
AI_DASHSCOPE_MODEL=替换为可用模型ID
```

上面是三个变量条目的示意，建议使用环境变量编辑窗口逐条添加。这里不要加 `export`。如果未显示该设置，可从 **Modify options** 中启用。参见 [IDEA 环境变量说明](https://www.jetbrains.com/help/idea/program-arguments-and-environment-variables.html)。

### 5.2 在终端中设置环境变量

Shell 中使用：

```bash
export AI_DASHSCOPE_API_KEY='替换为有效密钥'
export AI_DASHSCOPE_BASE_URL='https://替换为控制台提供的服务域名'
export AI_DASHSCOPE_MODEL='替换为可用模型ID'
```

`export` 影响当前 Shell 及之后由它启动的子进程，不会给已经运行的 IDEA 或 Java 进程补充变量。仅在 IDEA 内置 Terminal 中执行 `export`，也不会自动改变 Run 按钮使用的运行配置。

可以只检查变量是否存在，避免打印密钥：

```bash
if [ -n "${AI_DASHSCOPE_API_KEY:-}" ]; then
  echo 'API Key 已设置'
else
  echo 'API Key 未设置'
fi
```

> **注意：** `~/.bash_profile`、`~/.zshrc`、`~/.zprofile` 的加载条件不同。把变量写入某个文件，不等于任意启动方式都能读取。密钥不要写入学习文档、源码仓库或日志。

## 6. 创建模型配置类

位置：`src/main/java/tanyi/saa/hellowrld/config/SaaConfig.java`。

配置类完成两件事：

1. 创建 `DashScopeApi`，配置 API Key 和服务基地址。
2. 创建 `DashScopeChatModel`，作为 `ChatModel` Bean 供 Controller 注入。

当前关键配置为：

```java
@Value("${spring.ai.dashscope.api-key}")
private String apiKey;

@Value("${spring.ai.dashscope.base-url}")
private String baseUrl;

@Value("${spring.ai.dashscope.chat.options.model}")
private String model;

@Bean
public DashScopeApi dashScopeApi() {
    return DashScopeApi.builder()
            .apiKey(apiKey)
            .baseUrl(baseUrl)
            .build();
}

@Bean
public ChatModel chatModel() {
    return DashScopeChatModel.builder()
            .dashScopeApi(dashScopeApi())
            .build();
}
```

### 注意点：读取了 model，不等于模型配置生效

**当前源码存在一个待完善点：** `model` 字段虽然读取了 YAML 配置，但 `chatModel()` 没有使用这个字段。不能据此认为手动创建的 `DashScopeChatModel` 已使用 YAML 中指定的模型。

若继续采用手动创建 Bean 的方式，可将上面的 `chatModel()` 替换为以下示例，并添加 import。**以下是改进示例，尚未应用到源码。**

```java
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;

/**
 * 创建使用指定模型的聊天客户端。
 * @return 聊天模型
 */
@Bean
public ChatModel chatModel() {
    DashScopeChatOptions options = new DashScopeChatOptions();
    options.setModel(model);
    return DashScopeChatModel.builder()
            .dashScopeApi(dashScopeApi())
            .defaultOptions(options)
            .build();
}
```

上面使用的 `setModel` 和 `defaultOptions` 已核对本地 `spring-ai-alibaba-dashscope:1.1.2.0` 的 API。也可以学习 starter 的自动配置方式，但不要将“手动创建 Bean”和“所有 YAML 属性都会自动应用”混为一谈。

### 注意点：地址协议与接口协议

`base-url` 应包含 `https://`，不能仅填写域名。域名前缀中的 `ws-` 不代表 WebSocket 协议。

当前 `DashScopeApi` 文本生成调用使用如下路径：

```text
/api/v1/services/aigc/text-generation/generation
```

不要把这个完整接口路径再次填入服务基地址，也不要把 OpenAI 兼容接口的 `/compatible-mode/v1` 地址直接当作本例 DashScope 原生接口的基地址。客户端协议、服务地域、工作空间和密钥需要对应。

## 7. 创建聊天 Controller

位置：`src/main/java/tanyi/saa/hellowrld/controller/HelloWorldController.java`。当前代码如下：

```java
package tanyi.saa.hellowrld.controller;

import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Description 第一个ai类
 * Author
 * Date 2026/9/18
 * Version 1.0
 **/
@RestController
public class HelloWorldController {

    private static final Logger log = LoggerFactory.getLogger(HelloWorldController.class);

    @Resource
    private ChatModel chatModel;

    /**
     * 异步执行同步模型调用，一次性返回完整回答。
     * @param msg 用户输入
     * @return 完整回答的异步结果
     */
    @GetMapping("/doChat")
    public Mono<String> doChat(@RequestParam(name = "msg", defaultValue = "你是谁") String msg) {
        // 用户输入可能包含敏感信息，只记录长度。
        log.info("doChat input: msgLength={}", msg.length());
        // 同步模型调用可能阻塞，必须离开 WebFlux 的事件循环线程执行。
        return Mono.fromCallable(() -> chatModel.call(msg))
                .subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * 使用流式输出
     * @param msg
     * @return
     */
    @GetMapping("/doStream")
    public Flux<String> doStream(@RequestParam(name = "msg", defaultValue = "你是谁") String msg) {
        return chatModel.stream(msg);
    }


}
```

### 7.1 完整回答：Mono + boundedElastic

`chatModel.call(msg)` 是同步调用。WebFlux 的 `reactor-http-nio-*` 线程负责处理网络事件，不适合执行这类阻塞操作。直接在其中调用可能报：

```text
block()/blockFirst()/blockLast() are blocking,
which is not supported in thread reactor-http-nio-*
```

当前修复方式是延迟执行同步调用，并切换到允许阻塞的工作线程：

```java
return Mono.fromCallable(() -> chatModel.call(msg))
        .subscribeOn(Schedulers.boundedElastic());
```

`Mono<String>` 表示异步产生一个回答，并不意味着把回答拆成流式片段。浏览器仍收到完整文本。这个写法遵循 [Reactor 包装阻塞调用的建议](https://projectreactor.io/docs/core/release/reference/faq.html)。

> **注意：** 不要改成 `Mono.just(chatModel.call(msg))`，因为调用会在构造 Mono 之前执行；也不要在 Controller 中最后加 `.block()`。只套一层 Mono 而不安排执行线程，也没有解决同步调用占用事件循环的问题。

### 7.2 流式回答：Flux

`chatModel.stream(msg)` 返回文本片段序列，当前 `/doStream` 直接返回该 `Flux<String>`。

> **注意：** 返回 Flux 不等于浏览器一定逐字显示。响应媒体类型、客户端处理和代理缓冲都会影响观察效果。当前注解没有显式声明 SSE；若希望固定使用 SSE，可学习下方改进示例。

```java
import org.springframework.http.MediaType;

@GetMapping(value = "/doStream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
```

这里只展示可选注解改法，尚未修改当前 Controller。SSE 客户端应解析 `data:` 事件内容，不要把整个响应当作单个 JSON 文档。

## 8. 编译、启动与访问

### 8.1 检查环境并编译

在父工程目录执行：

```bash
cd /Users/tanyi/Desktop/study/AI/SpringAiAlibaba
java -version
mvn -v
mvn -pl SSA-01HelloWorld -am compile
```

`-pl` 选择子模块，`-am` 同时构建其需要的 reactor 项目。编译成功只说明代码和依赖可以编译，不代表密钥有效或外部模型接口可用。

需要查看最终依赖时：

```bash
mvn -pl SSA-01HelloWorld dependency:tree
mvn -pl SSA-01HelloWorld help:effective-pom
```

### 8.2 启动应用

重新加载 Maven，在 IDEA 中运行 `SpringHelloWorldApplication.main()`。确认运行配置选择 JDK 21，且环境变量已设置。

日志中应出现类似信息：

```text
Netty started on port 8081 (http)
Started SpringHelloWorldApplication
```

> **注意：** 当前 POM 未声明 Spring Boot Maven 打包插件。不要默认认为 `mvn package` 生成的普通 JAR 就能用 `java -jar` 启动；本步骤使用 IDEA 直接运行 main 方法。

### 8.3 访问完整回答接口

浏览器访问：`http://localhost:8081/doChat`。未传 `msg` 时，默认消息是“你是谁”。

也可以使用 curl 对中文参数进行 URL 编码：

```bash
curl --get 'http://localhost:8081/doChat' \
  --data-urlencode 'msg=请用一句话介绍你自己'
```

### 8.4 观察流式接口

```bash
curl -N --get 'http://localhost:8081/doStream' \
  -H 'Accept: text/event-stream' \
  --data-urlencode 'msg=请分三点介绍 Spring AI'
```

`-N` 关闭 curl 输出缓冲，`Accept` 请求 SSE 响应。若中间经过代理，还要检查代理是否缓存响应。调用真实模型服务会使用账号额度。

## 9. 常见错误与排查顺序

| 现象 | 优先检查 | 处理方式 |
| --- | --- | --- |
| 缺少 `ReactiveWebServerFactory` | 是否只有 `spring-webflux` 库而没有完整 Web starter | 引入 WebFlux starter，重新加载 Maven |
| `Could not resolve placeholder ''` | 是否写了 `@Value("${}")` | 使用完整配置键，如 `${spring.ai.dashscope.api-key}` |
| `Could not resolve placeholder 'AI_DASHSCOPE_API_KEY'` | 实际 Java 进程是否继承变量 | 在应用运行配置中设置变量，再重启 |
| `block()` 不允许在 `reactor-http-nio-*` 中执行 | 同步模型调用是否占用事件循环 | 使用 `fromCallable` 与 `boundedElastic` |
| URL 无效、协议错误或连接异常 | `https://`、服务基地址、地域和接口类型 | 对照控制台服务地址，避免重复拼接路径 |
| 修改 YAML 模型后效果不变 | 手动构建 ChatModel 时是否传入 `model` | 使用 `defaultOptions` 设置模型 |
| 401 / 403 | 密钥有效性、工作空间与模型权限 | 按服务端错误信息检查授权 |
| 模型不存在或不可用 | 模型 ID、地域及开通情况 | 使用当前工作空间支持的模型 |
| 8081 端口被占用 | 是否有旧应用实例运行 | 停止自己的旧实例，或修改端口并更新访问地址 |
| 编译提示不支持目标版本 21 | Maven 实际使用的 JDK | 用 `mvn -v` 核对，并选择 JDK 21 |
| Flux 响应未逐段显示 | Content-Type、客户端和代理缓冲 | 检查 SSE 协商，使用 `curl -N` 观察 |

建议按“依赖可编译 → 应用能启动 → 配置能读取 → HTTP 路由正常 → 模型鉴权与响应正常”的顺序排查，避免同时修改多个无关配置。

## 10. 学习验收清单

以下清单供后续实际操作使用，未勾选项不代表本次已执行验证：

- [ ] 能说明 `<modules>` 与 `<parent>` 的区别。
- [ ] 能说明 BOM 管理版本与引入依赖的区别。
- [ ] Maven 和应用运行配置均使用 JDK 21。
- [ ] Java 进程能读取 API Key，日志不打印密钥。
- [ ] 服务基地址包含 HTTPS 协议，接口类型与客户端匹配。
- [ ] 手动构建模型时，期望使用的 model 已传入默认选项。
- [ ] `/doChat` 返回完整回答，且未发生事件循环阻塞异常。
- [ ] 能观察 `/doStream` 的流式响应，并理解 SSE 和 Flux 的区别。

**本次文档核对范围：** 已阅读父子 POM、启动类、配置类、Controller 和脱敏后的 YAML；已核对模型选项相关本地 API。未重新编译应用，未调用真实模型服务，也未应用文中的可选改进示例。
