# ===== 构建阶段 =====
FROM node:20-alpine AS builder

WORKDIR /app

# 复制依赖配置
COPY package.json package-lock.json ./

# 安装依赖
RUN npm ci --only=production && npm cache clean --force

# 复制源码
COPY . .

# 构建前端
RUN npm run build

# ===== 运行阶段 =====
FROM nginx:1.25-alpine

# 复制构建产物到 nginx 静态目录
COPY --from=builder /app/dist /usr/share/nginx/html

# 复制 nginx 配置
COPY nginx.conf /etc/nginx/conf.d/default.conf

# 暴露端口
EXPOSE 80

# 健康检查
HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
  CMD wget -qO- http://localhost:80/ || exit 1

CMD ["nginx", "-g", "daemon off;"]
