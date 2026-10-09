# ===== 构建阶段 =====
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q clean package -DskipTests

# ===== 运行阶段 =====
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
ENV TZ=Asia/Shanghai
ENV LANG=C.UTF-8
# 安装中日韩字体（DroidSansFallbackFull.ttf，Apache-2.0，供 OpenPDF 内嵌生成中文 PDF）
RUN apt-get update \
    && apt-get install -y --no-install-recommends fonts-droid-fallback fontconfig \
    && fc-cache -f \
    && rm -rf /var/lib/apt/lists/*
RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone
COPY --from=build /build/target/trackmeet-backend.jar /app/app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-Dfile.encoding=UTF-8", "-jar", "/app/app.jar", "--spring.profiles.active=prod"]
