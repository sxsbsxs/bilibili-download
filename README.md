# Bilibili Download

本项目正在以 [KafuuNeko/BiliDownload](https://github.com/KafuuNeko/BiliDownload) 为基础进行原生 Android 源码迁移和修改。

上游项目采用 GNU GPL v3.0。本仓库保留上游版权与许可要求；迁移后的整体衍生作品按 GPL-3.0 发布。上游原作者及项目的版权归其各自权利人所有。

## 本分支迁移目标

- 以 BVD 的 Android 原生 Kotlin 项目结构、界面和业务实现为基础，而不是 WebView 网页壳。
- 保留 BVD 的视频搜索、解析、下载、资源管理等实现，并在迁移完成后逐项适配。
- 界面中文化，底部主导航按需求保留「首页」「我的」。
- 游客可搜索公开内容；账号登录用于需要账号权限的功能和更高清晰度。
- 所有改动均在独立分支进行，尚未合并到 main。

## 上游来源

- 项目：[KafuuNeko/BiliDownload](https://github.com/KafuuNeko/BiliDownload)
- 上游许可证：GNU General Public License v3.0
- 本项目的修改与迁移说明将在后续提交中持续更新。

## 当前状态

源码迁移进行中。当前分支尚不能视为完整移植版本；功能和构建状态以实际提交及 GitHub Actions 结果为准。
