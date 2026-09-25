# YOKULI OS 品牌资产

2026-09-25 后续用户附件取代此前的独立 Y 标记。唯一编辑母版为 `yokuli-wordmark.svg`：大写 YOKULI 占主位，帆在 O 内，水线连接 O，OS 位于右下角。这里是附件轮廓的平面矢量适配，不包含原图纸纹和光照渐变。

`python3 scripts/generate_brand_assets.py` 生成 11 个生产 VectorDrawable、`yokuli-mark.svg` 和 `yokuli-os.svg`。不要分别编辑生成文件，也不要让旧版生成器重新写回 Y 图形。小尺寸图标只是完整字标中 O、帆和水线的提取，不是另一个独立字母商标。

`YokuliBrandMark` 用于现有小标记入口；`YokuliBrandWordmark` 为完整字标的单色版本；`YokuliBrandSignature` 用于关于页等完整签名位置，不再在左边重复拼标记。安装图标、主题单色图标、系统启动窗口与上述 Compose 组件使用同一母版。

纸白 `#FAF9F6`、墨色 `#1F2A30`、帆绿 `#69877F`、水线 `#5C706D`、辅助灰 `#757D80`。共享界面通过 `WpThemePolicy` 接入同一低饱和色系；保留用户选定的其他强调色，以及海图、警报、未知/过期等业务语义颜色。`CYAN` 作为已存储键保留，其内置海事配色更新为帆绿。

本文件覆盖旧 W10M 设计文档中的“独立 Y / 旧青色”品牌描述；W10M 的控件结构、字号、访问与 Home 动效契约保持不变。更新品牌不等于逐页重做所有业务 UI。
