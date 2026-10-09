package com.trackmeet.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;

/**
 * 单进程部署模式：由后端直接托管前端静态资源（供 Windows EXE 安装包使用）。
 *
 * <p>启用方式：{@code --trackmeet.serve-ui=true}（安装包默认开启；Docker + Nginx 部署无需开启）。</p>
 *
 * <p>行为：</p>
 * <ol>
 *   <li>静态资源优先从 {@code classpath:/static/} 与运行目录下的 {@code ./frontend/} 查找；</li>
 *   <li>找不到且请求的不是静态文件（无扩展名）时回退到 {@code index.html}，从而支持前端 history 路由；</li>
 *   <li>{@code /api/**}、{@code /actuator/**} 等由各自的 Controller 处理，不受影响。</li>
 * </ol>
 *
 * @author TrackMeet
 */
@Slf4j
@Configuration
@ConditionalOnProperty(name = "trackmeet.serve-ui", havingValue = "true")
public class SpaWebConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        log.info("已启用单进程部署模式：后端将托管前端静态资源（classpath:/static/ 与 ./frontend/）");
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/", "file:./frontend/", "file:./static/")
                .resourceChain(true)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(String resourcePath, Resource location) throws IOException {
                        Resource requested = location.createRelative(resourcePath);
                        if (requested.exists() && requested.isReadable()) {
                            return requested;
                        }
                        // 非静态文件（无扩展名）→ SPA 回退到 index.html
                        if (resourcePath.contains(".")) {
                            return null;
                        }
                        Resource index = location.createRelative("index.html");
                        if (index.exists() && index.isReadable()) {
                            return index;
                        }
                        Resource classpathIndex = new ClassPathResource("/static/index.html");
                        return classpathIndex.exists() ? classpathIndex : null;
                    }
                });
    }
}
