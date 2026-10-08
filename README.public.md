<!-- 仓库对外版 README 的源文件；发布时以 README.md 上传到仓库。 -->

# DuraHUD

> 在 HUD 上显示盔甲、主手、副手与饰品耐久的 Minecraft Fabric 客户端模组。**不携带任何 GUI 贴图**：所有视觉元素都按原版 UV 从原版图集里取，因此替换这些图集的资源包会自动生效。

Minecraft 1.20.1 · Fabric · 纯客户端 · MIT

## 特性

- **固定格**：头盔、胸甲、护腿、靴子、主手、副手；开启 Trinkets 支持后额外显示已装备饰品的耐久（最多 12 格）。
- **耐久条 + 数值**：数值可以关闭、只显示百分比，或显示上下两行（第一行剩余、第二行上限）。
- **阈值提示**：低于警告 / 危险阈值时变色，危险时可叠加原版选中格的高亮框。
- **动画**：格子出现与消失的淡入淡出、空槽位补位时的平滑移动、同一格换物品时旧内容移出淡出 / 新内容移入淡入；掉耐久时耐久条长度与数值走同一段加减动画。动画可整体关闭，关闭后条与数字一帧到位。
- **布局**：跟随原版 GUI 缩放与窗口尺寸；锚点可选快捷栏上方 / 左侧 / 右侧、屏幕四角或自定义绝对坐标；支持横排与竖排；越界时自动拉回屏幕内。
- **编辑模式**：游戏中按 H 直接拖动 HUD 摆放位置，方向键微调，滚轮缩放。
- **资源包友好**：零自定义 GUI 贴图，覆盖原版 GUI 图集的资源包（例如 Cozy UI）无需为本模组做适配。
- **零分配渲染**：绘制路径每帧不产生临时对象。

## 安装

1. Minecraft 1.20.1 + Fabric Loader >= 0.15.11；
2. 安装 **Fabric API**（必需）；
3. 把 durahud-<版本>.jar 放进 mods 目录；
4. 可选：**ModMenu**（装好后模组列表里会出现配置入口）、**Trinkets 3.x**（显示饰品耐久）。

本模组声明为 client 环境：服务端不需要安装，多人游戏里也不会因为服务端没装而出问题。

## 使用

| 按键 | 作用 |
|---|---|
| K | 打开设置界面 |
| H | 切换 HUD 编辑模式 |

编辑模式内的操作：左键拖拽移动（自动切到 CUSTOM 锚点）、方向键微调 1px（按住 Shift 步长乘 10）、滚轮或 = 与 - 缩放、R 复位位置、ESC 或 H 退出。

设置界面按用途分 6 页：显示 / 物品 / 布局 / 外观 / 警示 / 动画；底部可以导出、导入预设（.txt，走系统文件选择框）。

## 配置

配置文件是 config/durahud.json，首次运行时自动生成。

| 字段 | 说明 | 默认 |
|---|---|---|
| enabled | 总开关，关闭后完全不绘制 | true |
| anchor | 锚点：HOTBAR_ABOVE / HOTBAR_LEFT / HOTBAR_RIGHT / TOP_LEFT / TOP_RIGHT / BOTTOM_LEFT / BOTTOM_RIGHT / CUSTOM（CUSTOM 时 offsetX/Y 是绝对坐标） | HOTBAR_RIGHT |
| orientation | HORIZONTAL（一行格子）/ VERTICAL（一列格子） | HORIZONTAL |
| barPlacement | 耐久条相对格子的方向：ABOVE / BELOW / LEFT / RIGHT | ABOVE |
| valuePlacement | 数值方向：AUTO / ABOVE / BELOW / LEFT / RIGHT（AUTO = 自动放到条的对侧） | ABOVE |
| barLength | 耐久条长度（像素）；0 = 自动（上下方 22、左右 60），上限 182 | 0 |
| offsetX / offsetY | 相对锚点的偏移；CUSTOM 时是绝对坐标 | 0 |
| scale | 整体缩放 0.25 - 4.0 | 1.0 |
| showMainhand / showOffhand / showArmor | 分别显示主手 / 副手 / 盔甲四格 | true |
| showTrinkets | 显示 Trinkets 已装备饰品的耐久（未安装 Trinkets 时无作用） | true |
| onlyDurableItems | 只显示带耐久信息的物品 | true |
| showWornNonDurable | 例外：穿戴类格子里的物品即使没有耐久信息也显示 | false |
| hideFull | 满耐久时不画内容（格子仍占位，布局不动） | false |
| showEmptySlots | 空槽位也画框并占位；关闭后空槽位不占位、后面的格子自动补位 | true |
| valueDisplay | 数值显示：OFF / PERCENT / VALUE（VALUE 是上下两行：剩余 + 上限） | VALUE |
| valueOffsetY | 数值整体的纵向微调（-9..9，缩放前像素）；只动数字，格子与耐久条不动 | 0 |
| showSlotFrames / showItemIcons | 绘制格子边框 / 物品图标 | true |
| animateCells / animationMillis | 动画总开关 / 动画时长（50 - 1000 毫秒） | true / 200 |
| highlightCritical / warnThreshold / critThreshold | 危险高亮开关 / 警告阈值 % / 危险阈值 % | true / 50 / 20 |

几点约定：

- 枚举以常量名写入文件；无法识别的取值只会让**那一个字段**回退到默认值，不会让整份配置失效。
- 文件顶层的 configVersion 记录配置结构版本（当前 7）。读取时会依次执行迁移链（v0 -> v7），迁移是幂等的，只会改键的集合，不发明新键。
- 只在「首次运行」「文件版本落后（走过迁移）」「导入预设」时写盘，启动时不会覆盖手改过的文件。

## 从源码构建

需要 **JDK 17** 与 **Gradle 8.7**（本仓库不含 wrapper，因为上游构建环境就是这样跑的）：

```bash
gradle build     # 产物：build/libs/durahud-<version>.jar
gradle test      # 单元测试（82 个用例，纯 JVM，不需要启动游戏）
```

几何层与配置层刻意不依赖 Minecraft 的类型，所以布局计算、配置迁移、动画曲线这些逻辑都能脱离游戏跑单元测试（见 src/test/java）。

### 编译期的可选依赖：Trinkets

为了直接调用 Trinkets 的 API，编译需要 Trinkets 3.7.2 的 jar，以及它的两个 jar-in-jar 依赖（cardinal-components-base / cardinal-components-entity）：

1. 把 Trinkets 的 mod jar 放在任意位置，在 gradle.properties 里用 trinkets_jar 指向它（仓库里的值只是占位符）；
2. 用 node tools/extract-cca.mjs 可以把两个 CCA jar 从 Trinkets 的 jar 里抽出来，再用 cca_base_jar / cca_entity_jar 指向它们。

不需要饰品支持时：注释掉 build.gradle 里引用这三个文件的 modCompileOnly 行，并删掉 hud/TrinketsBridge.java 及其在 hud/ExtraSlots.java 里的调用即可。运行时没有安装 Trinkets 的用户本来就不受影响（ExtraSlots 里有 isModLoaded 守卫，TrinketsBridge 不会被加载）。

## 为什么不携带贴图

模组里唯一的 PNG 是图标 assets/durahud/icon.png（128x128，给 ModMenu 显示）。HUD 的每一个像素都来自原版图集：

- 格子边框 = widgets.png 里原版副手槽位的精灵；
- 耐久条 = bars.png 里原版耐久条的底槽与填充；
- 危险高亮 = widgets.png 里原版物品栏选中格的高亮框。

因此任何覆盖这些原版图集的资源包都会自动生效，本模组不需要为它们做特殊适配，也不需要自己的 GUI 贴图。

## 项目结构

```
src/main/java/dev/durahud/
  DuraHudClient.java        入口点：注册 HUD 回调与按键
  config/                   配置数据、读写与迁移、设置界面、控件、预设导入导出
  hud/                      布局几何、图集 UV、逐格状态与动画、渲染、编辑模式、饰品桥
src/main/resources/         模组元数据、语言文件、图标
src/test/java/dev/durahud/  纯 JVM 单元测试（不依赖 Minecraft）
tools/                      开发脚本：版本号递增、无头布局预览、API 清单、资源包取图
```

## 已知限制

- 只支持 Minecraft 1.20.1：模组 jar 与具体游戏版本、映射绑定，不是「任意 Fabric 版本都能装」。
- 需要 Fabric API；不支持 Forge / NeoForge。
- 设置界面完全用原版 Screen / ButtonWidget / SliderWidget 实现，没有引入 Cloth Config。

## 许可证

MIT，见 LICENSE.md。Trinkets、ModMenu、Fabric API 都是可选或必需的**外部**依赖，不包含在本仓库里。
