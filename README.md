# AdSkipper - 广告跳过助手

一个基于 Android 无障碍服务的开源广告自动跳过工具。通过自定义规则自动检测并跳过各类应用的广告。

> 编译顺利啊!!!

## 功能特性

- **无障碍服务** - 利用 Android AccessibilityService 后台监听窗口变化
- **自定义规则** - 支持通过文本、ID、类名、内容描述等多种条件匹配广告元素
- **规则录制** - 悬浮窗模式一键录制规则，自动捕获界面元素信息
- **JSON 导入导出** - 规则支持完整的 JSON 格式导入导出，便于分享和备份
- **Material Design 3** - 采用最新的 Material You 设计语言，支持动态取色
- **多种执行动作** - 支持点击、点击父元素、滑动、返回键、自定义坐标点击
- **规则管理** - 启用/禁用、优先级设置、搜索、批量操作
- **统计功能** - 记录跳过次数，查看规则使用效果
- **后台运行** - 无障碍服务天然保活，无需担心被杀后台
- **开源免费** - 完全开源，无广告，无追踪

## 系统要求

- Android 8.0+ (API 26+)
- 授予无障碍服务权限
- 悬浮窗权限（规则录制功能需要）

## 安装方法

### 方法一：GitHub Actions 自动编译

Fork 本仓库后，GitHub Actions 会自动编译 APK。你可以在 Actions 页面下载编译好的 APK 文件。

### 方法二：本地编译

1. 克隆仓库
   ```bash
   git clone https://github.com/yourusername/AdSkipper.git
   cd AdSkipper
   ```

2. 使用 Android Studio 打开项目，或使用命令行编译：
   ```bash
   ./gradlew assembleDebug
   ```

3. 编译完成后，APK 位于 `app/build/outputs/apk/debug/` 目录下

### 方法三：直接安装

下载 releases 页面中的 APK 文件，直接在 Android 设备上安装。

> **注意**：由于使用了无障碍服务，部分应用市场可能无法上架。建议从 GitHub Releases 直接下载。

## 使用指南

### 首次使用

1. 打开应用，点击"前往设置开启"按钮
2. 在无障碍设置中找到"AdSkipper"并开启
3. 返回应用，服务状态会显示"服务运行中"

### 创建规则

#### 方式一：手动添加

1. 点击底部"规则"标签
2. 点击右下角"+"按钮
3. 填写规则信息：
   - **规则名称**：给规则起个名字
   - **目标应用**：输入应用包名（如 `com.example.app`），或点击按钮选择已安装的应用
   - **触发条件**：至少选择一种匹配条件（文本/ID/类名/内容描述）
   - **执行动作**：选择跳过广告的操作方式
4. 点击"保存"

#### 方式二：悬浮窗录制

1. 在主页点击"录制规则"
2. 授予悬浮窗权限
3. 选择目标应用
4. 打开目标应用，进入广告页面
5. 点击悬浮窗上的"录制"按钮
6. 点击广告上的"跳过"按钮或关闭按钮
7. 系统自动捕获元素信息并保存为规则

### 导入导出规则

#### 导出规则

- 在规则页面点击右上角菜单，选择"导出规则"
- 或在主页点击"导出规则"快捷卡片
- 规则将保存为 JSON 文件到 Downloads 目录

#### 导入规则

- 在规则页面点击右上角菜单，选择"导入规则"
- 选择 JSON 规则文件
- 支持合并（保留现有规则）和替换两种模式
- 也支持从剪贴板直接导入

### 规则 JSON 格式

```json
{
  "version": 1,
  "exportTime": 1700000000000,
  "appVersion": "1.0.0",
  "rules": [
    {
      "name": "跳过开屏广告",
      "description": "跳过某应用的开屏广告",
      "packageName": "com.example.app",
      "enabled": true,
      "priority": 10,
      "useText": true,
      "targetText": "跳过",
      "textMatchType": "contains",
      "actionType": "click"
    }
  ]
}
```

### 匹配方式说明

| 匹配方式 | 说明 | 示例 |
|---------|------|------|
| 包含 | 目标文本包含关键词 | "跳过" 匹配 "点击跳过" |
| 完全匹配 | 完全相同的文本 | "关闭广告" 只匹配 "关闭广告" |
| 开头匹配 | 以目标文本开头 | "跳过" 匹配 "跳过广告" |
| 结尾匹配 | 以目标文本结尾 | "广告" 匹配 "关闭广告" |
| 正则表达式 | 使用正则匹配 | `^跳过\\d+s$` 匹配 "跳过5s" |

### 执行动作说明

| 动作 | 说明 |
|------|------|
| 点击 | 直接点击匹配到的元素 |
| 点击父元素 | 点击匹配元素的父级元素 |
| 左滑 | 在元素位置执行左滑手势 |
| 右滑 | 在元素位置执行右滑手势 |
| 返回键 | 模拟按下返回键 |
| 自定义点击 | 在指定坐标位置点击 |

## 项目结构

```
app/src/main/java/com/adskipper/
├── AdSkipperApplication.kt          # 应用入口
├── MainActivity.kt                   # 主 Activity
├── RuleEditorActivity.kt             # 规则编辑器
├── service/
│   ├── AdSkipAccessibilityService.kt # 核心无障碍服务
│   └── FloatingWindowService.kt      # 悬浮窗录制服务
├── data/
│   ├── RuleEntity.kt                 # 规则数据实体
│   ├── RuleDao.kt                    # Room 数据库访问对象
│   ├── RuleDatabase.kt               # 数据库配置
│   ├── RuleManager.kt                # 规则管理器
│   └── RuleJsonModels.kt             # JSON 序列化模型
├── ui/
│   ├── theme/                        # Material 3 主题
│   ├── screen/                       # Compose 页面
│   └── viewmodel/                    # ViewModel
├── util/
│   ├── JsonExporter.kt               # JSON 导出工具
│   ├── JsonImporter.kt               # JSON 导入工具
│   └── SettingsManager.kt            # 设置管理
└── receiver/
    └── BootReceiver.kt               # 开机启动接收器
```

## 技术栈

- **Kotlin** - 编程语言
- **Jetpack Compose** - UI 框架
- **Material Design 3** - 设计系统
- **Room** - 本地数据库
- **DataStore** - 偏好设置存储
- **Gson** - JSON 序列化
- **Coroutines/Flow** - 异步编程
- **AccessibilityService** - 无障碍服务 API

## CI/CD

本项目配置了 GitHub Actions 工作流：

- **Build** - 每次 push 自动编译 Debug 和 Release APK
- **CI** - 运行 Lint 检查和单元测试
- **Release** - main 分支自动创建 Release 并上传 APK

## 常见问题

### 无障碍服务自动关闭

部分国产系统会频繁杀后台。请将应用加入电池优化白名单，并锁定后台。

### 规则不生效

1. 检查无障碍服务是否已开启
2. 检查规则是否已启用（开关状态）
3. 检查包名是否正确
4. 尝试使用更宽松的匹配条件（如"包含"而非"完全匹配"）

### 悬浮窗无法显示

需要在系统设置中授予"显示在其他应用上层"权限。

### 耗电问题

无障碍服务本身几乎不耗电。只在窗口内容变化时触发检查，平时处于休眠状态。

## 贡献指南

欢迎提交 Issue 和 PR！

1. Fork 本仓库
2. 创建你的功能分支 (`git checkout -b feature/AmazingFeature`)
3. 提交更改 (`git commit -m 'Add some AmazingFeature'`)
4. 推送到分支 (`git push origin feature/AmazingFeature`)
5. 打开一个 Pull Request

## 许可证

本项目基于 [MIT](LICENSE) 许可证开源。

## 免责声明

本工具仅供学习研究使用。使用本工具跳过广告时，请遵守相关应用的使用条款。开发者不对因使用本工具而产生的任何纠纷负责。