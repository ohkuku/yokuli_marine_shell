# 官方海图下载站

消费者入口为 [`index.html`](index.html)。页面使用现有 `design/brand` 品牌、黑白版式及中英文内容，按海域与来源搜索、按大洲筛选，展现真实版本/内容/大小/来源。原件与旧版本折叠；上传中的包不会显示有效下载按钮。没有下载器或网站自有数据库，也不把数据包实体放进 Pages。

目录的唯一维护源、字段和发行规则在 [chart-library/README.md](../../chart-library/README.md#维护全球资料目录)。执行 `python3 scripts/publish_chart_catalogue.py` 同时生成这里的 `catalogue.json`、APK 的离线目录快照以及共用品牌资源。不得手工编辑这些生成文件。页面标签文案在 `site.js`；新的地区、版本、来源或文件只改源目录。

网站入口：`https://ohkuku.github.io/yokuli_marine_shell/charts/`。Pages 发布源为独立 `gh-pages` 分支的 `/(root)`，不要求新的构建或 CI 工作流。大文件链接始终指向 Git LFS 实体；发布状态只有在实体成功上传之后才能切换为 `published`。


发布前先生成目录与开发者资源：

```sh
python3 scripts/publish_chart_catalogue.py
python3 sdk/tools/package_extensions.py
python3 scripts/publish_docs_pages.py
# 看过输出的临时 checkout 后，明确执行发布
python3 scripts/publish_docs_pages.py --push
```

默认脚本只从当前 docs 准备并暂存网站。`--push` 才创建必要的网站提交并使用 CLI 普通推送；不修改 CI、Git 配置、登录或 Pages 设置。它读取远程既有 gh-pages，保留无关站点内容，仅替换这里的 charts、developers 与根入口；首次无分支时才建孤儿分支。不复制海图载荷、源码仓库、隐藏配置或密钥。站点目录未同步源 catalogue 时会拒绝发布。

应用保存目录为 `Documents/Yokuli OS Documents/Chart Packages/<大洲>/<国家>/<collectionId>/<fileName>`；网页仍由浏览器决定保存位置。稳定系列目录隔离各地区和原件/原生版本，具体文件按真实发行版本命名。
