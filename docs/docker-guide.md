# Docker 部署指南

本文说明如何使用 Docker 和旧版 `docker-compose` 一键部署、维护及更新
NetCheck FastAPI 后端。Compose 文件采用 `3.3` 格式，并已面向经典
`docker-compose` v1 客户端设计。

## 环境要求

- Docker Engine 20.10 或更高版本
- `docker-compose` 1.25 或更高版本
- 服务器的 13000/TCP 端口可用

确认版本：

```bash
docker --version
docker-compose --version
```

## 首次启动

在项目根目录执行：

```bash
docker-compose up -d --build
```

该命令会构建 Python 3.11 镜像、启动后端，并创建持久化数据库卷
`netcheck_data`。服务默认地址如下：

- 管理页面：`http://服务器地址:13000/`
- 健康检查：`http://服务器地址:13000/health`
- API 文档：`http://服务器地址:13000/docs`

验证服务：

```bash
docker-compose ps
curl --fail http://127.0.0.1:13000/health
```

如果需要使用其他宿主机端口，例如 8080：

```bash
NETCHECK_PORT=8080 docker-compose up -d --build
```

后续执行 Compose 命令时需继续传入相同的 `NETCHECK_PORT`，或者在项目根目录
创建 `.env` 并写入 `NETCHECK_PORT=8080`。

## 日常管理

查看容器状态和日志：

```bash
docker-compose ps
docker-compose logs --tail=200 backend
docker-compose logs -f backend
```

重启、停止及重新启动：

```bash
docker-compose restart backend
docker-compose stop
docker-compose start
```

停止并移除容器和网络：

```bash
docker-compose down
```

`docker-compose down` 不会删除数据库卷。不要随意执行
`docker-compose down -v`，因为 `-v` 会永久删除 SQLite 数据。

## 更新部署

从 Git 仓库拉取代码并重建服务：

```bash
git pull
docker-compose up -d --build
docker-compose ps
curl --fail http://127.0.0.1:13000/health
```

Compose 只会替换应用容器，`netcheck_data` 数据卷会保留。若要强制重新构建
所有镜像层：

```bash
docker-compose build --no-cache backend
docker-compose up -d backend
```

## 数据库与备份

容器内数据库路径为 `/data/diagnostic.db`，由命名卷持久化。查看实际卷名：

```bash
docker volume ls | grep netcheck_data
docker-compose exec -T backend ls -lh /data/diagnostic.db
```

在线创建一致性备份并复制到当前目录：

```bash
docker-compose exec -T backend python -c "import sqlite3; source=sqlite3.connect('/data/diagnostic.db'); backup=sqlite3.connect('/data/diagnostic-backup.db'); source.backup(backup); backup.close(); source.close()"
container_id=$(docker-compose ps -q backend)
docker cp "$container_id:/data/diagnostic-backup.db" ./diagnostic-backup.db
```

备份文件可能包含设备和网络诊断信息，应限制访问权限并妥善保管。

## 故障排查

配置解析失败时，先确认当前客户端能够读取 Compose 3.3：

```bash
docker-compose config
```

服务未通过健康检查时查看日志：

```bash
docker-compose ps
docker-compose logs --tail=200 backend
```

若提示 13000 端口已占用，请通过 `NETCHECK_PORT` 改用其他端口。若提示数据库
没有写权限，检查容器是否仍挂载 `netcheck_data:/data`，不要手工替换卷内文件的
所有者。生产环境还应在服务前配置 Nginx 或 Caddy、HTTPS 和访问控制；当前 API
允许所有 CORS 来源且没有身份认证，不适合直接暴露到公网。
