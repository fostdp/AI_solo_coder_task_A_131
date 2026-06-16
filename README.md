# 🏛️ 古代巢车结构仿真与视野分析系统

春秋战国时期巢车（古代侦查车）复原研究系统。集成传感器数据采集、悬臂梁结构仿真、基于地理高程的通视分析、实时告警推送，以及三维可视化。

---

## 系统架构

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                                   监控层                                         │
│                          Prometheus (端口 9090)                                  │
│                    ← 抓取 /actuator/prometheus                                   │
└─────────────────────────────────────────────────────────────────────────────────┘
                                                ↑
┌─────────────────────────────────────────────────────────────────────────────────┐
│                                前端展示层 (端口 80)                               │
│  ┌───────────────┐  ┌────────────────┐  ┌──────────────────┐                    │
│  │ NestCart 3D   │  │ 结构仿真 Canvas │  │ 视野分析 Canvas   │                    │
│  │ (Three.js)    │  │ 应力/挠度/弯矩  │  │ 通视覆盖热力图    │                    │
│  └───────────────┘  └────────────────┘  └──────────────────┘                    │
│            │                      │                      │                       │
│            └──────────────────────┴──────────────────────┘                       │
│                                   │ Nginx (Gzip 压缩)                              │
│                      ↕ REST / WebSocket (STOMP)                                   │
└─────────────────────────────────────────────────────────────────────────────────┘
                                                ↑
┌─────────────────────────────────────────────────────────────────────────────────┐
│                            后端服务层 (端口 8080)                                  │
│  ┌───────────────── SpringBoot 模块化 ─────────────────────┐                      │
│  │  📡 dtu_receiver  │  ⚙️ structural_simulator │  👁️ visibility_analyzer │  🔔 alarm_ws │
│  │  数据采集+校验    │   梁单元应力/Davenport风谱 │   通视算法/四叉树加速 │  告警评估/WSPush │
│  └────────────── Spring Events 事件总线 ───────────────────┘                      │
│            ↑                ↑                ↑                  ↑                  │
│     REST API        SensorDataReceivedEvent   SensorDataReceivedEvent  SensorDataReceivedEvent
│       ↑                                                                ↓           │
│       │                                                        WebSocket /topic/alerts
│       │                                                                ↓           │
└───────┼───────────────────────────────────────────────────────────────────────────┘
        │                                                          ↑
        │                MQTT Pub                          MQTT Sub (告警广播预留)
        │     ┌───────────────────────────────────────┐           │
        └─────┤         MQTT Broker (端口 1883)        │───────────┘
              │           Eclipse Mosquitto            │
              │   主题: nestcart/sensor/{cartId}/data  │
              └──────────────────┬────────────────────┘
                                 ↑ MQTT Pub
              ┌──────────────────┴────────────────────┐
              │           传感器模拟器层                │
              │  simulator-cart1 (丘陵地形, 高度 6~15m) │
              │  simulator-cart2 (山地地形, 高度 8~18m) │
              └───────────────────────────────────────┘
                                                ↑
┌─────────────────────────────────────────────────────────────────────────────────┐
│                            数据层 (端口 5432)                                    │
│                    PostgreSQL 16 (BRIN/BTree/GIN 多索引优化)                     │
│  ┌──────────┐ ┌────────────┐ ┌─────────────┐ ┌─────────────┐ ┌───────────────┐ │
│  │nest_cart │ │sensor_data │ │ alert_record│ │  terrain_   │ │vision_analysis│ │
│  │  巢车配置 │ │ 时序传感器 │ │   告警记录   │ │  elevation  │ │    结果       │ │
│  └──────────┘ └────────────┘ └─────────────┘ └─────────────┘ └───────────────┘ │
└─────────────────────────────────────────────────────────────────────────────────┘
```

---

## 模块组成

| 模块 | 技术栈 | 端口 | 说明 |
|------|--------|------|------|
| **frontend** | Nginx 1.27 + Three.js r128 | 80 | 前端可视化 + API 反代，启用 Gzip 压缩 |
| **backend** | SpringBoot 3.2 + JDK 17 | 8080 | 后端服务，4模块 + Spring Events 解耦 |
| **postgres** | PostgreSQL 16 Alpine | 5432 | 数据持久化，BRIN/BTree/GIN 索引优化 |
| **mosquitto** | Eclipse Mosquitto 2.0 | 1883 | MQTT Broker，传感器数据消息总线 |
| **prometheus** | Prometheus 2.53 | 9090 | 指标采集与监控，抓取 `/actuator/prometheus` |
| **simulator-cart1** | Python 3.12 | — | 巢车一号模拟器（丘陵地形） |
| **simulator-cart2** | Python 3.12 | — | 巢车二号模拟器（山地地形） |

---

## 快速部署

### 前置要求

- Docker ≥ 24.0
- Docker Compose ≥ 2.20
- 可用资源：建议 2C4G 及以上

### 一键启动

```bash
# 克隆项目
git clone <repo-url>
cd AI_solo_coder_task_A_131

# 构建并启动所有服务（首次构建约 3-5 分钟）
docker-compose up -d --build

# 查看服务状态
docker-compose ps
```

### 服务启动顺序（已配置健康检查自动编排）

1. **postgres** → 初始化数据库 + 地形数据 + 建索引
2. **mosquitto** → MQTT Broker 就绪
3. **backend** → SpringBoot（依赖前两者健康通过）
4. **frontend** / **prometheus** / **simulator-cart1** / **simulator-cart2**

### 访问入口

| 服务 | 地址 | 说明 |
|------|------|------|
| 前端首页 | http://localhost/ | 三维可视化 + 仿真 + 告警 |
| 后端 API | http://localhost/api/ | 通过 Nginx 反代 |
| Actuator 健康 | http://localhost/actuator/health | JSON 格式健康检查 |
| Prometheus 指标 | http://localhost/actuator/prometheus | Prometheus 抓取端点 |
| Prometheus UI | http://localhost:9090/ | Prometheus 控制台 |
| PostgreSQL | localhost:5432 | 数据库直连（user: nestcart / pass: nestcart123） |
| MQTT Broker | localhost:1883 | MQTT 客户端接入 |

### 停止与清理

```bash
# 停止服务
docker-compose stop

# 停止并删除容器（保留数据卷）
docker-compose down

# 彻底清理（含数据卷，谨慎使用）
docker-compose down -v
```

---

## 传感器模拟器用法

### Docker Compose 内置

`docker-compose.yml` 已内置两个模拟器实例，分别模拟不同地形：

| 实例 | 巢车 ID | 地形 | 高度范围 | 基础风速 | 上报间隔 |
|------|---------|------|---------|---------|---------|
| simulator-cart1 | a1b2c3d4-e5f6-7890-abcd-ef1234567890 | hilly（丘陵） | 6~15 m | 3 m/s | 30 s |
| simulator-cart2 | b2c3d4e5-f6a7-8901-bcde-f12345678901 | mountain（山地） | 8~18 m | 5 m/s | 45 s |

查看模拟器日志：

```bash
docker-compose logs -f simulator-cart1
docker-compose logs -f simulator-cart2
```

### 本地独立运行

```bash
cd simulator
pip install -r requirements.txt

# 最简用法（默认连接本地后端 + MQTT）
python simulator.py

# 指定高度和地形
python simulator.py \
  --height-base 12.0 \
  --height-min 5.0 \
  --height-max 20.0 \
  --terrain-profile valley \
  --terrain-affect \
  --interval 10

# 仅用 HTTP，禁用 MQTT
python simulator.py --no-mqtt --interval 5

# 连接远端 MQTT Broker（带认证）
python simulator.py \
  --mqtt-broker mqtt.example.com \
  --mqtt-port 1883 \
  --mqtt-username admin \
  --mqtt-password secret
```

### 可配置参数

所有参数同时支持 **命令行参数** 与 **环境变量**：

| 参数 | 环境变量 | 默认值 | 说明 |
|------|---------|--------|------|
| `--api-url` | `API_URL` | http://localhost:8080 | 后端 API 地址 |
| `--cart-id` | `CART_ID` | a1b2c3d4-... | 巢车 UUID |
| `--interval` | `INTERVAL` | 60 | 上报间隔（秒） |
| `--wind-base` | `WIND_BASE` | 3.0 | 基础风速 m/s |
| `--height-base` | `HEIGHT_BASE` | 10.0 | 基础高度 m |
| `--height-min` | `HEIGHT_MIN` | 4.0 | 最小高度 m |
| `--height-max` | `HEIGHT_MAX` | 18.0 | 最大高度 m |
| `--terrain-region` | `TERRAIN_REGION` | default_battlefield | 地形区域名 |
| `--terrain-profile` | `TERRAIN_PROFILE` | hilly | 地形轮廓：flat/hilly/mountain/valley |
| `--terrain-affect` | `TERRAIN_AFFECT` | false | 地形是否影响有效观察高度 |
| `--no-mqtt` | `DISABLE_MQTT` | false | 禁用 MQTT，仅 HTTP |
| `--mqtt-broker` | `MQTT_BROKER` | localhost | MQTT Broker 地址 |
| `--mqtt-port` | `MQTT_PORT` | 1883 | MQTT Broker 端口 |
| `--mqtt-username` | `MQTT_USERNAME` | — | MQTT 用户名 |
| `--mqtt-password` | `MQTT_PASSWORD` | — | MQTT 密码 |

### MQTT 消息规范

**上报主题：** `nestcart/sensor/{cart_id}/data`

**消息载荷（JSON）：**

```json
{
  "cartId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "boomStress": 4523180.52,
  "basketSway": 0.0872,
  "height": 12.35,
  "observationDistance": 9876.4,
  "windSpeed": 7.23,
  "windDirection": 125.4,
  "temperature": 22.6
}
```

---

## 关键工程化特性

### 🔬 监控与可观测性

- **Spring Boot Actuator**：暴露 `health`、`info`、`prometheus`、`metrics` 端点
- **自定义 Micrometer 指标**（在 Prometheus 中查询）：
  - `nestcart_sensor_data_received_total` — 传感器数据接收总数
  - `nestcart_structure_simulated_total` — 结构仿真执行次数
  - `nestcart_vision_analyzed_total` — 视野分析执行次数
  - `nestcart_alert_triggered_total` — 告警触发总数（含 WARNING/CRITICAL 分维度）
  - `nestcart_structure_simulation_duration_seconds` — 结构仿真耗时
  - `nestcart_vision_analysis_duration_seconds` — 视野分析耗时
- **Prometheus** 每 15s 自动抓取

### 💾 PostgreSQL 性能优化

- **时序表 BRIN 索引**：`sensor_data.timestamp`、`alert_record.created_at`、`vision_analysis_result.created_at`，适合时序高写入场景，空间占用极低
- **BTree 复合索引**：`(cart_id, timestamp DESC)`、`(severity, created_at DESC)`、`(region_name, grid_x, grid_y)` 等
- **GIN 索引**：`vision_analysis_result.visible_grid`（JSONB 字段）
- **部分索引**：`idx_alert_record_unacked` 仅索引未确认告警
- **PostgreSQL 参数调优**：`shared_buffers=1GB`、`effective_cache_size=3GB`、`max_wal_size=4GB`、`G1GC` 友好参数

### ⚡ 前端性能优化

- **Nginx Gzip 压缩**：覆盖 CSS/JS/JSON/XML/字体/图片等，压缩级别 6
- **静态资源长缓存**：CSS/JS/图片设置 7 天 `Cache-Control: public, immutable`
- **HTML 不缓存**：保证单页应用更新及时
- **反向代理**：API 和 WebSocket 统一反代至后端，支持长连接 keepalive

### 🐳 Docker 多阶段构建

后端 SpringBoot 使用两阶段构建：
1. **Builder 阶段**（`maven:3.9-eclipse-temurin-17`）：`dependency:go-offline` + 编译打包，仅含 JDK + Maven
2. **Runtime 阶段**（`eclipse-temurin:17-jre-alpine`）：只拷贝 fat jar，镜像小（约 180MB），JVM 参数适配容器（`MaxRAMPercentage=75`）

---

## 目录结构

```
AI_solo_coder_task_A_131/
├── backend/                       # SpringBoot 后端
│   ├── src/main/java/com/nestcart/
│   │   ├── alarm/                 # alarm_ws 模块：告警评估+WebSocket
│   │   ├── config/                # 配置类（WebSocket/MQTT/Properties）
│   │   ├── controller/            # REST API 兼容层
│   │   ├── dtu/                   # dtu_receiver 模块：数据采集+校验
│   │   ├── dto/                   # 数据传输对象
│   │   ├── entity/                # JPA 实体
│   │   ├── event/                 # Spring Events 事件类
│   │   ├── monitor/               # 自定义 Micrometer 指标
│   │   ├── repository/            # JPA Repository
│   │   ├── structure/             # structural_simulator 模块
│   │   ├── visibility/            # visibility_analyzer 模块
│   │   └── NestCartApplication.java
│   ├── src/main/resources/
│   │   └── application.yml        # 全部配置外置
│   ├── Dockerfile                 # 多阶段构建
│   └── pom.xml
├── frontend/                      # 前端（Three.js + Canvas）
│   ├── js/
│   │   ├── nest_chariot_3d.js     # 巢车三维渲染
│   │   ├── visibility_panel.js    # 视野分析面板组件
│   │   ├── structure-canvas.js    # 结构仿真图表
│   │   ├── vision-canvas.js       # 视野热力图
│   │   └── app.js                 # 主应用
│   ├── css/style.css
│   ├── index.html
│   ├── nginx.conf                 # Nginx 配置（含 Gzip）
│   └── Dockerfile
├── simulator/                     # MQTT + HTTP 双栈传感器模拟器
│   ├── simulator.py               # 核心逻辑（高度/地形可配置）
│   ├── requirements.txt
│   └── Dockerfile
├── sql/                           # 数据库脚本
│   ├── init.sql                   # 建表 + 初始数据 + 多类型索引
│   └── postgresql-tune.conf       # PostgreSQL 性能参数
├── monitoring/
│   └── prometheus.yml             # Prometheus 抓取配置
├── docker-compose.yml             # 一键编排
└── README.md
```

---

## API 速览

| Method | Path | 说明 |
|--------|------|------|
| GET | `/api/carts` | 巢车列表 |
| POST | `/api/carts/{id}/sensor-data` | HTTP 上报传感器数据 |
| GET | `/api/carts/{id}/sensor-data` | 历史传感器数据 |
| POST | `/api/simulation/structure/{cartId}` | 运行结构仿真 |
| GET | `/api/simulation/structure/{cartId}/latest` | 用最新传感器数据仿真 |
| POST | `/api/simulation/vision/{cartId}` | 运行视野分析 |
| GET | `/api/simulation/vision/horizon?height=10` | 计算理论视距 |
| GET | `/api/alerts` | 未确认告警列表 |
| PUT | `/api/alerts/{alertId}/acknowledge` | 确认告警 |
| GET | `/api/terrain/{regionName}` | 查询区域地形 |
| POST | `/api/dtu/{cartId}/sensor-data` | （新模块路径）DTU 上报 |
| POST | `/api/structure/simulate/{cartId}` | （新模块路径）结构仿真 |
| POST | `/api/vision/analyze/{cartId}` | （新模块路径）视野分析 |
| WS | `/ws/alerts` | STOMP WebSocket 实时告警推送（订阅 `/topic/alerts`） |

---

## License

学术研究用途。
