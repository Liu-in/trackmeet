#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
TrackMeet 发布打包脚本。

用法：
    python tools/package_release.py                 # 生成 dist/TrackMeet-<版本>-<日期>.zip
    python tools/package_release.py --version P6.1  # 指定版本号
    python tools/package_release.py --no-manifest   # 不写入发布清单

产物：
    dist/TrackMeet-<版本>-<yyyyMMdd>.zip            源码发布包（含 sql/backend/frontend/deploy/docs/tools）
    dist/TrackMeet-<版本>-<yyyyMMdd>.zip.sha256     包校验文件

排除：node_modules、target、dist、.git、.pnpm-store、__pycache__、*.pyc、*.log、编辑器目录与历史打包产物。
"""

import argparse
import hashlib
import shutil
import sys
import zipfile
from datetime import datetime
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DIST = ROOT / "dist"

EXCLUDE_DIRS = {
    "node_modules", "target", "dist", ".git", ".pnpm-store", "__pycache__",
    ".idea", ".vscode", ".mvn", ".gradle", "coverage", ".nyc_output",
}
EXCLUDE_SUFFIX = (".pyc", ".log", ".zip", ".tar", ".gz", ".sha256", ".tmp", ".bak")
EXCLUDE_NAMES = {".DS_Store", "Thumbs.db", "package-lock.json"}

# 统计分区（按路径前缀顺序匹配，命中即归类）
AREAS = [
    ("sql/", "数据库脚本（建表/初始化/演示与验收数据/增量补丁）"),
    ("backend/src/main/", "后端主代码（引擎 + 10 个业务模块）"),
    ("backend/src/test/", "后端测试（单元测试 + 集成测试 + 测试配置）"),
    ("backend/", "后端工程文件（pom / Dockerfile / 配置）"),
    ("frontend/src/", "前端源码（api/stores/router/views/components/styles）"),
    ("frontend/", "前端工程文件（package.json / vite / tsconfig / Dockerfile）"),
    ("deploy/", "部署与运维（Compose / Nginx / 备份恢复 / 冒烟脚本 / 手册）"),
    ("docs/", "设计文档（需求/API/字段清单/各阶段说明与验收清单）"),
    ("tools/", "质量工具（后端一致性检查 / 前端页面测试 / 打包脚本）"),
    ("README.md", "项目说明"),
]
RUN_HINT = """\
================================================================================
TrackMeet 田径运动会编排软件 —— 源码发布包
================================================================================
一、快速启动（Docker Compose，推荐）
    cd deploy
    cp .env.example .env         # Windows: Copy-Item .env.example .env
    # 修改 .env 中的 MYSQL_ROOT_PASSWORD / DB_PASSWORD / JWT_SECRET
    docker compose up -d --build
    # 首次启动自动按序执行 sql/01…10（建表 → 初始化 → 演示数据 → 验收数据）
    ./scripts/smoke-test.sh      # 冒烟验收（Windows: pwsh ./scripts/smoke-test.ps1）

二、访问入口
    管理端        http://localhost/
    后端 API      http://localhost:8080/api
    大屏公告(匿名) http://localhost/screen/public-board?meetId=1&token=demo0000000000000000000000000001

三、默认账号（首次启动由 DefaultAccountInitializer 初始化口令 Admin@123）
    admin / Admin@123       超级管理员
    scheduler / Admin@123   编排员
    referee / Admin@123     裁判
    leader1 / Admin@123     领队（示例市第一中学）

四、本地开发
    cd backend  && mvnw spring-boot:run -Dspring-boot.run.profiles=dev
    cd frontend && pnpm install && pnpm dev

五、测试与质量门禁
    python tools/check_project.py            # 后端静态一致性
    python tools/check_frontend.py           # 前端关键页面测试（26 页 / 257 项）
    cd backend && mvnw test                  # 单元测试 + 集成测试（194 个用例）
    cd deploy  && ./scripts/smoke-test.sh    # 运行期冒烟（TC-SMOKE-01…16）

六、文档索引
    README.md                                  安装 / 启动 / 默认账号 / 使用流程
    docs/05-API清单.md                          全部 REST 接口与权限编码
    docs/16、17、18、19                         各阶段算法说明与端到端测试用例
    docs/20-验收测试清单.md                     最终验收清单（8 大类 70+ 条）
    deploy/README.md                            部署手册（变量表/升级/备份恢复/故障排查）

七、注意
    1) 本包为源码包，不含 node_modules 与构建产物；首次部署需构建镜像（约 5~10 分钟）。
    2) sql/06 与 sql/10 为演示与验收数据，生产环境请勿导入。
    3) 中文 PDF 依赖可嵌入字体：Docker 镜像已内置 DroidSansFallback，Windows 自动使用 simhei.ttf。
    4) 生产环境请立即修改默认口令、更换 JWT_SECRET、并在 .env 中限制 CORS 来源。
================================================================================
"""


def included(path: Path) -> bool:
    if any(part in EXCLUDE_DIRS for part in path.parts):
        return False
    if path.name in EXCLUDE_NAMES:
        return False
    if path.suffix.lower() in EXCLUDE_SUFFIX:
        return False
    return True


def area_of(rel: str) -> str:
    for prefix, label in AREAS:
        if rel.startswith(prefix):
            return label
    return "其他"


def sha256_of(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1 << 20), b""):
            digest.update(chunk)
    return digest.hexdigest()


def main() -> int:
    parser = argparse.ArgumentParser(description="TrackMeet 发布打包")
    parser.add_argument("--version", default="P6", help="版本号（默认 P6）")
    parser.add_argument("--date", default=datetime.now().strftime("%Y%m%d"), help="日期戳（默认今天）")
    parser.add_argument("--no-manifest", action="store_true", help="不写入发布清单")
    args = parser.parse_args()

    package_name = f"TrackMeet-{args.version}-{args.date}"
    DIST.mkdir(exist_ok=True)
    zip_path = DIST / f"{package_name}.zip"

    files = sorted(p for p in ROOT.rglob("*") if p.is_file() and included(p.relative_to(ROOT)))
    if not files:
        print("未找到可打包文件", file=sys.stderr)
        return 1

    counts = {}
    total_bytes = 0
    hashes = []
    for path in files:
        rel = path.relative_to(ROOT).as_posix()
        size = path.stat().st_size
        total_bytes += size
        counts[area_of(rel)] = counts.get(area_of(rel), 0) + 1
        hashes.append(f"{sha256_of(path)}  {rel}")

    manifest_lines = [
        "TrackMeet 田径运动会编排软件 —— 发布清单",
        "=" * 72,
        f"版本        : {args.version}",
        f"打包时间    : {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}",
        f"包内根目录  : {package_name}/",
        f"文件总数    : {len(files)}",
        f"源码总大小  : {total_bytes / 1024 / 1024:.2f} MB（压缩前）",
        "",
        "内容构成",
        "-" * 72,
    ]
    for label in sorted(counts, key=lambda item: -counts[item]):
        manifest_lines.append(f"  {counts[label]:>5} 个文件   {label}")
    manifest_lines += [
        "",
        "运行说明",
        "-" * 72,
        RUN_HINT.rstrip("\n"),
        "",
        "文件校验（SHA256）",
        "-" * 72,
    ]
    manifest_lines += hashes
    manifest_text = "\n".join(manifest_lines) + "\n"

    with zipfile.ZipFile(zip_path, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
        for path in files:
            rel = path.relative_to(ROOT).as_posix()
            archive.write(path, arcname=f"{package_name}/{rel}")
        if not args.no_manifest:
            archive.writestr(f"{package_name}/RELEASE-MANIFEST.txt", manifest_text)

    digest = sha256_of(zip_path)
    checksum_path = zip_path.with_suffix(".zip.sha256")
    checksum_path.write_text(f"{digest}  {zip_path.name}\n", encoding="utf-8")

    print("=" * 72)
    print(f"发布包：{zip_path.relative_to(ROOT)}")
    print(f"大小  ：{zip_path.stat().st_size / 1024 / 1024:.2f} MB（压缩后），源码 {total_bytes / 1024 / 1024:.2f} MB")
    print(f"文件数：{len(files)}（另含 RELEASE-MANIFEST.txt）")
    print(f"SHA256：{digest}")
    print(f"校验文件：{checksum_path.relative_to(ROOT)}")
    print("-" * 72)
    for label in sorted(counts, key=lambda item: -counts[item]):
        print(f"  {counts[label]:>5} 个文件   {label}")
    print("=" * 72)
    return 0


if __name__ == "__main__":
    sys.exit(main())
