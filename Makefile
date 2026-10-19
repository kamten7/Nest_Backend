# ============================================================================
# Nest 后端常用命令（Windows 无 make 时直接执行等价的 docker/mvn 命令）
# ============================================================================

.PHONY: help infra up down logs test package clean

help:
	@echo "make infra   # 只启动 MySQL/Redis/MinIO"
	@echo "make up      # 启动中间件 + 后端（首次自动构建镜像）"
	@echo "make down    # 停止容器（保留数据卷）"
	@echo "make logs    # 跟踪后端日志"
	@echo "make test    # 跑单元测试"
	@echo "make package # 打包（跳过测试）"

infra:
	docker compose up -d

up:
	docker compose --profile full up -d --build

down:
	docker compose down

logs:
	docker compose logs -f backend

test:
	mvn -B -ntp test

package:
	mvn -B -ntp -DskipTests package

clean:
	mvn -B -ntp clean
