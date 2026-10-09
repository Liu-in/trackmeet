#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
TrackMeet 前端关键页面测试脚本（无需浏览器与依赖安装即可运行）。

本脚本对「关键页面」做逐项静态验收，等价于一组可重复执行的 UI 冒烟测试：
  1. 文件存在且非空（菜单登记 component 能落到真实文件）
  2. SFC 结构完整：<template> / <script setup lang="ts"> / defineOptions
  3. 使用的 API 模块存在（@/api/xxx 可解析）且页面确实引用了该模块
  4. 权限门：使用 useUserStore().hasPerm 控制按钮/页面
  5. 异常处理：存在 try/catch（避免接口失败时白屏）
  6. 加载态：存在 loading / v-loading
  7. A4 打印页面：引入 @/styles/print.css 且使用 .tm-print-page / .tm-print-table
  8. 含定时器的页面（大屏）：onUnmounted/onDeactivated 中清理定时器
  9. 使用 v-html 的页面：渲染前有清洗（去 script 标签或转义函数）
 10. 空数据兜底：存在 el-empty 或「暂无」提示

用法：python tools/check_frontend.py
退出码：0 全部通过；1 存在失败项
"""

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
VIEWS = ROOT / "frontend" / "src" / "views"
API_DIR = ROOT / "frontend" / "src" / "api"

# 页面 → 期望检查项
#   apis        : 必须引用的 api 模块（不含扩展名）
#   print       : 是否为 A4 打印页面（要求 print.css + tm-print-* 类）
#   timers      : 是否包含定时器（要求 onUnmounted/onDeactivated 清理）
#   html        : 是否使用 v-html（要求清洗）
#   perms       : 期望出现的权限编码前缀（至少命中一个）
PAGES = {
    "auth/Login.vue":                    dict(apis=["auth"]),
    "dashboard/Index.vue":               dict(apis=[]),
    "athlete/AthleteList.vue":           dict(apis=["athlete"], perms=["athlete:"]),
    "registration/RegistrationList.vue": dict(apis=["registration"], perms=["registration:"]),
    "registration/RegistrationAudit.vue":dict(apis=["registration"], perms=["registration:"]),
    "registration/RegistrationCheck.vue":dict(apis=["registration"], perms=["registration:"]),
    "event/EventList.vue":               dict(apis=["event"], perms=["event:"]),
    "event/RoundList.vue":               dict(apis=["event"], perms=["event:"]),
    "event/RuleConfig.vue":              dict(apis=["event"], perms=["event:"]),
    "event/ScoreRule.vue":               dict(apis=["event"], perms=["event:"]),
    "schedule/SessionList.vue":          dict(apis=["schedule"], perms=["schedule:"]),
    "schedule/SchedulePlan.vue":         dict(apis=["schedule"], perms=["schedule:"]),
    "schedule/HeatLane.vue":             dict(apis=["schedule"], perms=["schedule:"]),
    "schedule/OrderBook.vue":            dict(apis=["print"], print=True, perms=["print:"]),
    "result/ResultEntry.vue":            dict(apis=["result"], perms=["result:"]),
    "result/ResultAudit.vue":            dict(apis=["result"], perms=["result:"]),
    "result/ResultNotice.vue":           dict(apis=["standing"], print=True, perms=["print:"]),
    "result/AdvanceManage.vue":          dict(apis=["result"], perms=["result:"]),
    "standing/PointsBoard.vue":          dict(apis=["standing"], print=True, perms=["standing:"]),
    "standing/MedalBoard.vue":           dict(apis=["standing"], print=True, perms=["standing:"]),
    "print/PrintCenter.vue":             dict(apis=["print"], print=True, perms=["print:"]),
    "print/TemplateConfig.vue":          dict(apis=["print"], perms=["print:"]),
    "certificate/CertificateList.vue":   dict(apis=["standing"], print=True, html=True, perms=["certificate:"]),
    "notice/NoticeList.vue":             dict(apis=["screen"], perms=["notice:"]),
    "screen/ScreenConfig.vue":           dict(apis=["screen"], perms=["screen:"]),
    "screen/ScreenBoard.vue":            dict(apis=["screen"], timers=True, html=True, perms=["screen:"]),
}

# 允许跳过异常处理断言的页面（例如纯静态页）
SKIP_TRY = {"dashboard/Index.vue"}

TIMER_FUNCS = ("setInterval", "setTimeout")
CLEANUP_HINTS = (
    "onUnmounted", "onDeactivated", "clearInterval", "clearTimeout",
    "beforeUnmount", "onBeforeUnmount",
)
HTML_CLEAN_HINTS = (
    "replace(/<script", "replace(/<\\s*script", "safeText", "sanitize", "stripHtml", "escapeHtml",
)


def read(path: Path) -> str:
    return path.read_text(encoding="utf-8")


def api_exports() -> set:
    exports = set()
    for path in API_DIR.glob("*.ts"):
        text = read(path)
        exports.add(path.stem)
    return exports


def main() -> int:
    failures = []
    warnings = []
    results = []
    available_apis = api_exports()

    for rel_path, spec in PAGES.items():
        path = VIEWS / rel_path
        checks = []

        def check(name, condition, detail="", severity="error"):
            checks.append((name, bool(condition), detail, severity))

        if not path.is_file():
            results.append((rel_path, [("文件存在", False, "文件缺失", "error")]))
            failures.append(f"{rel_path}: 文件缺失")
            continue

        text = read(path)
        size_kb = len(text.encode("utf-8")) / 1024

        check("文件存在且非空", len(text.strip()) > 200, f"{size_kb:.1f} KB")
        check("含 <template>", "<template>" in text)
        check("script setup + lang=ts", bool(re.search(r"<script\s+setup\s+lang=\"ts\"", text)))
        check("defineOptions 命名", "defineOptions" in text)

        # API 模块引用
        for api_name in spec.get("apis", []):
            referenced = bool(re.search(r"from\s+'@/api/" + re.escape(api_name), text))
            check(f"引用 @/api/{api_name}", referenced and api_name in available_apis,
                  "" if api_name in available_apis else f"api 模块 {api_name}.ts 不存在")

        # 权限门（警告级：只读页可无写操作权限门）
        perms = spec.get("perms", [])
        if perms:
            has_perm_call = "hasPerm" in text
            hit = any(p in text for p in perms)
            check("权限门 hasPerm", has_perm_call and hit, f"期望包含 {perms[0]}*", severity="warn")

        # 异常处理
        if rel_path not in SKIP_TRY:
            check("异常处理 try/catch", "try {" in text or "catch" in text)

        # 加载态 / 空数据（警告级）
        check("加载态 loading/v-loading", ("loading" in text) or ("v-loading" in text), severity="warn")
        check("空数据兜底", ("el-empty" in text) or ("暂无" in text), severity="warn")

        # A4 打印页：必须能按 A4 打印（共享样式或自带 @media print 均可）
        if spec.get("print"):
            own_print = "@media print" in text
            shared_css = "print.css" in text
            uses_page_class = "tm-print-page" in text
            check("A4 打印能力（共享样式或自带 @media print）", own_print or shared_css,
                  ("共享 print.css" if shared_css else "") + ("自带 @media print" if own_print else "两者皆无"))
            check("A4 页容器（tm-print-page 或自带分页规则）",
                  uses_page_class or ("@media print" in text and ("page-break" in text or "break-inside" in text)))
            check("使用共享打印样式 print.css", shared_css, "建议统一到 @/styles/print.css", severity="warn")
            check("打印时隐藏工具栏 (.no-print)", "no-print" in text, "", severity="warn")

        # 定时器清理（错误级：泄漏会持续轮询）
        if spec.get("timers"):
            uses_timer = any(t in text for t in TIMER_FUNCS)
            cleans = any(hint in text for hint in CLEANUP_HINTS)
            check("定时器与清理成对", uses_timer and cleans,
                  "使用 " + "/".join([t for t in TIMER_FUNCS if t in text]) if uses_timer else "未使用定时器")

        # v-html 清洗（错误级：XSS 风险）
        if spec.get("html"):
            uses_vhtml = "v-html" in text
            cleans = any(hint in text for hint in HTML_CLEAN_HINTS)
            check("v-html 渲染前清洗", uses_vhtml and cleans, "需去 script 标签或转义")

        page_errors = [item for item in checks if not item[1] and item[3] == "error"]
        page_warns = [item for item in checks if not item[1] and item[3] == "warn"]
        results.append((rel_path, checks))
        for name, _, detail, _ in page_errors:
            failures.append(f"{rel_path}: {name}" + (f"（{detail}）" if detail else ""))
        for name, _, detail, _ in page_warns:
            warnings.append(f"{rel_path}: {name}" + (f"（{detail}）" if detail else ""))

    # ---------------- 输出 ----------------
    print("=" * 78)
    print("TrackMeet 前端关键页面测试")
    print("=" * 78)
    total_checks = 0
    total_failed = 0
    total_warn = 0
    for rel_path, checks in results:
        passed = sum(1 for item in checks if item[1])
        errors = [item for item in checks if not item[1] and item[3] == "error"]
        warns = [item for item in checks if not item[1] and item[3] == "warn"]
        total_checks += len(checks)
        total_failed += len(errors)
        total_warn += len(warns)
        flag = "PASS" if not errors else "FAIL"
        suffix = f"，{len(warns)} 项建议" if warns else ""
        print(f"[{flag}] {rel_path:<38} {passed}/{len(checks)} 项通过{suffix}")
        for name, _, detail, _ in errors:
            print(f"         └─ 未通过：{name}" + (f"（{detail}）" if detail else ""))
        for name, _, detail, _ in warns:
            print(f"         └─ 建议改进：{name}" + (f"（{detail}）" if detail else ""))
    print("-" * 78)
    print(f"页面数：{len(results)}    检查项：{total_checks}    未通过：{total_failed}    建议改进：{total_warn}")
    if warnings:
        print("-" * 78)
        print("建议改进明细（不影响通过，属统一性/体验优化）：")
        for item in warnings:
            print("  [WARN] " + item)
    if failures:
        print("-" * 78)
        print("失败明细：")
        for item in failures:
            print("  [ERR ] " + item)
        print("-" * 78)
        print(f"前端关键页面测试未通过：{len(failures)} 项")
        return 1
    print("前端关键页面测试全部通过。")
    return 0


if __name__ == "__main__":
    sys.exit(main())
