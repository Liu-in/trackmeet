# ===== 构建阶段 =====
FROM node:20-alpine AS build
WORKDIR /build
# 复制锁文件并严格按锁安装，避免依赖漂移（pnpm-lock.yaml 由仓库维护）
COPY package.json pnpm-lock.yaml ./
RUN corepack enable \
    && pnpm config set registry https://registry.npmmirror.com \
    && pnpm install --frozen-lockfile
COPY . .
RUN pnpm run build

# ===== 运行阶段（Nginx 静态托管 + /api 反向代理）=====
FROM nginx:1.25-alpine
ENV TZ=Asia/Shanghai
RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone
COPY --from=build /build/dist /usr/share/nginx/html
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
