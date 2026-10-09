# 后端集成测试说明（backend/src/test/resources）

本目录下的配置只作用于测试运行，不会影响 `src/main/resources` 的生产配置。

| 文件 | 作用 |
| --- | --- |
| `application.yml` | 测试 classpath 的基础配置（覆盖主配置，避免 `spring.profiles.active: dev` 被激活而连到开发库）；并兜底声明 `spring.profiles.active: test` |
| `application-test.yml` | 测试 profile 专属配置：测试库数据源、关闭验证码、降噪日志、`spring.sql.init.mode=never` |

集成测试类位于 `backend/src/test/java/com/trackmeet/api/`，全部继承
`com.trackmeet.api.support.ApiTestBase`（`@SpringBootTest(webEnvironment = RANDOM_PORT)` +
`@AutoConfigureMockMvc` + `@ActiveProfiles("test")`）。

---

## 一、准备测试库（三选一）

> 测试库库名固定为 `trackmeet_test`（可在 `application-test.yml` 用 `TEST_DB_URL` 覆盖）。
> 数据由外部脚本准备：**必须按序执行 `sql/01-schema-system.sql` ~ `sql/06-demo-data.sql`**
> （`07/08/09` 为增量补丁，首次全新建库已由 01~04 覆盖，可按需执行）。
> `06-demo-data.sql` 提供演示届次 id=1、4 个组别、4 个单位、16 名运动员与演示账号
> （`admin` / `scheduler` / `referee` / `leader1`，初始口令由 `trackmeet.default-password` 决定，默认 `Admin@123`）。

### 方式 1：本机 MySQL 8 手工执行

```bash
mysql -h 127.0.0.1 -P 3306 -uroot -p -e "
CREATE DATABASE IF NOT EXISTS trackmeet_test DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE USER IF NOT EXISTS 'trackmeet'@'%' IDENTIFIED BY 'trackmeet@123';
GRANT ALL PRIVILEGES ON trackmeet_test.* TO 'trackmeet'@'%';
FLUSH PRIVILEGES;"

cd /path/to/repo
for f in sql/01*.sql sql/02*.sql sql/03*.sql sql/04*.sql sql/05*.sql sql/06*.sql; do
  mysql -h 127.0.0.1 -uroot -p trackmeet_test < "$f"
done
```

### 方式 2：用 deploy/docker-compose 起 MySQL，再在其上初始化测试库

`deploy/docker-compose.yml` 的 `mysql` 服务会把 `sql/` 挂载为
`/docker-entrypoint-initdb.d`，**仅在数据卷为空时**按文件名顺序自动初始化
`${DB_NAME:-trackmeet}` 库。测试库需要单独创建并导入：

```bash
cd deploy && cp .env.example .env && docker compose up -d mysql
# 等待 healthy
docker compose ps

# 建测试库并授权（口令与 deploy/.env 的 DB_PASSWORD 保持一致）
docker exec -i trackmeet-mysql mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -e "
CREATE DATABASE IF NOT EXISTS trackmeet_test DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE USER IF NOT EXISTS 'trackmeet'@'%' IDENTIFIED BY 'trackmeet@123';
GRANT ALL PRIVILEGES ON trackmeet_test.* TO 'trackmeet'@'%';
FLUSH PRIVILEGES;"

# 导入 01~06（在宿主机仓库根目录执行）
for f in sql/01*.sql sql/02*.sql sql/03*.sql sql/04*.sql sql/05*.sql sql/06*.sql; do
  docker exec -i trackmeet-mysql mysql -uroot -p"$MYSQL_ROOT_PASSWORD" trackmeet_test < "$f"
done
```

容器内 mysql 客户端也可直接读取挂载目录：

```bash
docker exec -i trackmeet-mysql sh -c 'for f in /docker-entrypoint-initdb.d/0[1-6]*.sql; do mysql -uroot -p"$MYSQL_ROOT_PASSWORD" trackmeet_test < "$f"; done'
```

### 方式 3：CI 用 service 容器（GitHub Actions 示例）

```yaml
services:
  mysql:
    image: mysql:8.0
    env:
      MYSQL_ROOT_PASSWORD: root
      MYSQL_DATABASE: trackmeet_test
      MYSQL_USER: trackmeet
      MYSQL_PASSWORD: trackmeet@123
    ports: ["3306:3306"]
    options: >-
      --health-cmd="mysqladmin ping -h 127.0.0.1 -uroot -proot --silent"
      --health-interval=10s --health-timeout=5s --health-retries=30

steps:
  - uses: actions/checkout@v4
  - uses: actions/setup-java@v4
    with: { distribution: temurin, java-version: '17', cache: maven }
  - name: 初始化测试库
    run: |
      for f in sql/01*.sql sql/02*.sql sql/03*.sql sql/04*.sql sql/05*.sql sql/06*.sql; do
        mysql -h 127.0.0.1 -P 3306 -uroot -proot trackmeet_test < "$f"
      done
  - name: 运行测试
    working-directory: backend
    env:
      TEST_DB_URL: jdbc:mysql://127.0.0.1:3306/trackmeet_test?useUnicode=true&characterEncoding=utf8&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai
      TEST_DB_USERNAME: trackmeet
      TEST_DB_PASSWORD: trackmeet@123
    run: mvn -B test
```

---

## 二、运行方式

在 `backend/` 目录下执行（仓库当前未包含 Maven Wrapper；如已添加则把 `mvn` 换成 `./mvnw`）：

| 目的 | 命令 |
| --- | --- |
| 全部测试（单元 + 集成） | `mvn test` |
| 只跑集成测试（类名以 `IT` 结尾） | `mvn test -Dtest='*IT'` |
| 只跑引擎纯单测 | `mvn test -Dtest='com.trackmeet.engine.**'` |
| 单个测试类 | `mvn test -Dtest=MeetApiIT` |
| 单个用例 | `mvn test -Dtest='MeetApiIT#shouldPageMeets'` |
| 全量校验（含打包） | `mvn verify` |

`pom.xml` 的 `maven-surefire-plugin` 显式声明了 `includes`（保留 surefire 默认的
`Test* / *Test / *Tests / *TestCase` 模式，并追加 `**/*IT.java`），因此集成测试随
`mvn test` 一起执行，无需额外插件。

---

## 三、环境变量覆盖

`application-test.yml` 中的数据源全部使用占位符，可被环境变量覆盖（优先级：环境变量 > yml 默认值）：

| 环境变量 | 默认值 |
| --- | --- |
| `TEST_DB_URL` | `jdbc:mysql://127.0.0.1:3306/trackmeet_test?useUnicode=true&characterEncoding=utf8&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai` |
| `TEST_DB_USERNAME` | `trackmeet` |
| `TEST_DB_PASSWORD` | `trackmeet@123` |

示例：

```bash
TEST_DB_USERNAME=root TEST_DB_PASSWORD=root mvn test -Dtest='*IT'
# Windows PowerShell
$env:TEST_DB_PASSWORD='root'; mvn test -Dtest='*IT'
```

其它可选覆盖：`-Dtrackmeet.captcha.enabled=false`（测试 profile 已关闭验证码，登录无需验证码）、
`-Dtrackmeet.pdf.font-path=/usr/share/fonts/truetype/droid/DroidSansFallbackFull.ttf`
（影响 `PrintApiIT` 走「PDF 可用」分支还是「返回 50003」分支）。

---

## 四、跳过（SKIP）策略

集成测试**不会**因为「本机没有 MySQL」而失败，而是被标记为 **SKIPPED**：

1. `spring.datasource.hikari.initialization-fail-timeout: -1`（见 `application-test.yml`）——
   数据库不可用时 Hikari 不在上下文启动阶段快速失败，Spring 上下文仍可正常启动；
2. `ApiTestBase` 在每个用例前执行就绪检查（`@BeforeEach assumeEnvironmentReady()`，结论在 JVM 内缓存，仅首次真实探测）：
   - 能否连接数据源、`sys_user` 是否非空、是否存在演示届次 `meet.id = 1`（即 `sql/06-demo-data.sql` 是否已导入）；
   - 内置账号 `admin` 能否用默认口令登录；
   任一不满足即调用 JUnit `Assumptions.assumeTrue(false, ...)` 中止该用例 → 结果记为 **SKIPPED**；
3. 依赖演示账号（`scheduler` / `referee` / `leader1`）的权限用例通过 `assumeLogin(...)` 检查，
   未执行 `sql/06-demo-data.sql` 时自动跳过；`PrintApiIT` 的 PDF 用例按
   `GET /api/print/pdf/capability` 的 `available` 分支断言，两种情况都不误报失败。

> 结论：**无 MySQL 环境 → 集成测试全部 SKIPPED（不影响构建）；有库但未初始化 → 同样 SKIPPED；
> 有库且已初始化 → 正常执行。** 仅当测试库可用且脚本已导入时才会出现断言失败（即真实缺陷）。

---

## 五、用例编写约定

- 幂等：测试数据一律带时间戳 + 随机后缀（`uniqueName("测试单位")`），断言按名称前缀或项目 ID 过滤，
  不依赖用例执行顺序，也不依赖其它测试类创建的数据；
- 统一返回体断言：`assertOk`（HTTP 200 + `code=0`）、`assertPaged`（`data.records/total/page/size`）、
  `assertForbidden`（HTTP 403 + `code=403`）、`assertUnauthorized`（401）、`assertParamError`（HTTP 400 + `code=400`）、
  `assertBizCode` / `assertBizCodeIn`（业务码）；
- 不使用 `Thread.sleep` 长时间等待：需要等待最终一致时使用 `retryGet` / `retryPost`（最多 3 次、每次 200ms）；
- 禁止真正修改内置账号口令：`POST /api/auth/change-password` 只覆盖「参数校验」类用例，
  因为成功改密会使 `token_version` 自增并让所有测试缓存令牌失效。
