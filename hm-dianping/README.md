# HM-DianPing 项目说明

## 项目概述

HM-DianPing 是一个基于 Spring Boot 构建的本地生活服务平台后端系统，参考了大众点评的业务模式，提供商铺展示、优惠券管理、秒杀活动、用户社交等核心功能。

## 技术栈

| 技术                | 版本             | 说明             |
| :---------------- | :------------- | :------------- |
| Java              | 8              | 开发语言           |
| Spring Boot       | 2.3.12.RELEASE | 应用框架           |
| Spring Data Redis | -              | Redis 数据访问     |
| MyBatis Plus      | 3.4.3          | ORM 框架         |
| Redisson          | 3.13.6         | 分布式锁           |
| Hutool            | 5.7.17         | 工具库            |
| MySQL             | 5.1.47         | 数据库            |
| Nginx             | 1.18.0         | 前端静态资源服务（配套部署） |

## 核心功能

### 用户模块

- 手机号验证码登录/注册
- 用户信息管理
- 签到功能

### 商铺模块

- 商铺列表展示
- 商铺详情查询
- 商铺类型管理

### 优惠券模块

- 普通优惠券管理
- 秒杀优惠券管理
- 优惠券领取与使用

### 秒杀模块

- Redis 分布式秒杀
- Lua 脚本保证原子性
- 消息队列异步处理订单

## 项目结构

```plaintext
hm-dianping/
├── src/main/java/com/hmdp/
│   ├── controller/     # REST API 控制层
│   ├── service/        # 业务逻辑层
│   ├── mapper/         # 数据访问层
│   ├── entity/         # 数据库实体
│   ├── dto/            # 数据传输对象
│   ├── config/         # 配置类
│   ├── utils/          # 工具类
│   └── log/            # 日志相关
├── src/main/resources/
│   ├── mapper/         # MyBatis XML 配置
│   ├── db/             # 数据库初始化脚本
│   ├── seckill.lua     # 秒杀 Lua 脚本
│   └── unlock.lua      # 解锁 Lua 脚本
└── pom.xml             # Maven 依赖管理
```

## 数据库设计

### 核心数据表

| 表名                   | 说明      |
| :------------------- | :------ |
| tb\_user             | 用户信息表   |
| tb\_user\_info       | 用户详细信息表 |
| tb\_shop             | 商铺信息表   |
| tb\_shop\_type       | 商铺类型表   |
| tb\_voucher          | 优惠券表    |
| tb\_seckill\_voucher | 秒杀优惠券表  |
| tb\_voucher\_order   | 优惠券订单表  |
| tb\_blog             | 探店博客表   |
| tb\_blog\_comments   | 博客评论表   |
| tb\_follow           | 用户关注表   |
| tb\_sign             | 用户签到表   |

## 快速开始

### 环境要求

- JDK 1.8+
- MySQL 5.6+
- Redis 5.0+
- Maven 3.6+

### 部署步骤

1. **克隆项目**

```bash
git clone <repository-url>
cd hm-dianping
```

1. **配置数据库**

```sql
-- 创建数据库
CREATE DATABASE hmdp CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- 执行初始化脚本
source src/main/resources/db/hmdp.sql
```

1. **修改配置文件**

创建 `application.yml` 配置文件，配置数据库连接和 Redis 连接：

```yaml
server:
  port: 8080

spring:
  datasource:
    url: jdbc:mysql://localhost:3306/hmdp?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai
    username: your_username
    password: your_password
    driver-class-name: com.mysql.jdbc.Driver
  redis:
    host: localhost
    port: 6379
    password: your_redis_password
    lettuce:
      pool:
        max-active: 10
        max-idle: 10
        min-idle: 1
        time-between-eviction-runs: 10s

mybatis-plus:
  configuration:
    map-underscore-to-camel-case: true
    log-impl: org.apache.ibatis.logging.stdout.StdOutImpl
  global-config:
    db-config:
      id-type: auto
```

1. **启动项目**

```bash
mvn spring-boot:run
```

### 前端部署

前端静态资源位于 `nginx-1.18.0/html/hmdp/` 目录，配置 Nginx 反向代理：

```nginx
server {
    listen 80;
    server_name localhost;

    location / {
        root html/hmdp;
        index index.html;
    }

    location /api/ {
        proxy_pass http://localhost:8080/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }
}
```

## API 接口示例

### 用户登录

```http
POST /api/user/login
Content-Type: application/json

{
    "phone": "13686869696",
    "code": "123456"
}
```

### 获取商铺列表

```http
GET /api/shop/list?typeId=1&current=1&pageSize=10
```

### 秒杀优惠券

```http
POST /api/voucher-order/seckill/{voucherId}
```

## 核心技术实现

### Redis 缓存策略

- 商铺数据采用缓存穿透、击穿、雪崩解决方案
- 热点数据预热
- 缓存更新策略

### 分布式锁实现

- 使用 Redisson 实现分布式锁
- 支持可重入锁、公平锁、读写锁

### 秒杀优化

- Redis + Lua 脚本保证原子性
- 消息队列异步下单
- 限流与熔断

## 项目特点

- 完整的业务流程覆盖
- 完善的缓存策略
- 高并发场景优化
- 模块化架构设计
- 代码规范统一

## 许可证

MIT License

(本README由AI生成)
