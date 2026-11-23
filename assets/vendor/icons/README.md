# 本地图标子集

网页与原生组件库统一使用固定的 **Lucide** 语义 SVG 子集。`icon-data.js` 是生成的经典脚本，导出 `window.ABKIconData`，只含 `sources.json` 中列出的 30 个角色。网页支持直接打开 `file://`，原生 `AbkIcons.kt` 从同一份注册表生成，不运行时下载图标。

## 来源与许可

固定提交、原始图标名及路径以 [sources.json](sources.json) 为准。Lucide 使用 ISC；其中源自 Feather 的图标保留 Feather MIT 声明，见 [LICENSE-lucide](LICENSE-lucide)。不再分发 Tabler、Phosphor 或 Material Symbols 的比较资源。

SVG 保留原始 `viewBox`、路径、描边和填充方式。`info-mark` 专用于现有圆形状态载体：它沿固定上游竖线路径补回同一信息图标的 `i` 点；目录中的完整 `info` 角色仍单独保留。

## 重新生成

在仓库根目录运行：

```bash
python3 assets/vendor/icons/regenerate.py
node --check assets/vendor/icons/icon-data.js
python3 scripts/generate-compose-icons.py
```

脚本从固定提交下载、校验受支持的 SVG 元素与属性、保存许可证，再生成网页注册表。网络失败时不应覆盖现有注册表。原生生成器读取本地注册表并校验 30 个角色覆盖；不要手工编辑 `AbkIcons.kt`。

## 接入

网页按 `icon-data.js → abk-icons.js → abk-ui.js` 加载可选脚本；`abk-icons.js` 只渲染 Lucide，并在宿主保留备用符号。原生按 `AbkIcons` / `AbkIcon` 使用 `ImageVector`；装饰图标传 `null` 描述，独立图标按钮必须传本地化的可访问名称。
