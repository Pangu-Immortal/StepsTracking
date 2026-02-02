

# 📱 步数追踪应用 (Steps Tracking App)

<div align="center">

![萌萌计数器](https://count.getloli.com/get/@StepsTracking?theme=rule34)

</div>

<p align="center">
  <b>🌟 如果觉得有帮助，请点击 <a href="https://github.com/Pangu-Immortal/StepsTracking/stargazers">Star</a> 支持一下，关注不迷路！🌟</b>
</p>

📖 [打开英文版说明 The manual is available in English ](https://github.com/Pangu-Immortal/StepsTracking/blob/main/README.en.md)

## 🚶‍♂️ 项目简介

步数追踪应用是一款基于 Android 平台的健康类应用，用于记录和统计用户的每日步数数据。应用具有简洁的界面设计和高效的数据管理机制，让用户轻松掌握自己的日常活动量。

## ✨ 主要功能

- **📊 实时步数统计**：利用设备内置传感器记录用户步数
- **🔥 卡路里消耗计算**：根据步数自动计算卡路里消耗
- **🎯 每日目标设置**：默认6000步，可自定义目标
- **🔄 每日数据重置**：在每天0点自动重置步数记录
- **📅 周数据统计**：记录并展示过去一周的步数数据
- **🔗 Health Connect集成**：支持与Google Health Connect服务对接
- **📲 前台服务通知**：显示当前步数和完成进度

## 🔧 技术架构

- **📝 编程语言**：Kotlin
- **🏗️ 设计模式**：MVVM、单例模式、Repository模式
- **⚙️ 核心组件**：Android Sensor API、Foreground Service、Health Connect API
- **🎨 UI组件**：ConstraintLayout、CardView、ProgressBar

## 💻 环境配置要求

### 👨‍💻 开发环境

| 工具 | 最低版本要求                      |
| --- |-----------------------------|
| Android Studio | Meerkat Feature Drop 2024.3.2 RC 2 |
| Gradle | 8.13 或更高版本                   |
| JDK | 21                          |

### 📱 目标SDK版本

| SDK类型 | 版本 |
| --- | --- |
| 编译SDK版本 | 35 (Android 15) |
| 最低SDK版本 | 26 (Android 8.0 Oreo) |
| 目标SDK版本 | 35 (Android 15) |

### 📲 设备要求

- Android 8.0 (API 26) 或更高版本的Android设备
- 设备必须有步数传感器(STEP_COUNTER)
- 建议安装Google Health Connect应用以获取更准确的步数数据

## 🔐 权限说明

应用需要以下权限才能正常工作：

| 权限 | 用途 |
| --- | --- |
| `ACTIVITY_RECOGNITION` | 用于访问步数传感器数据 |
| `FOREGROUND_SERVICE` | 用于保持步数计数服务在后台运行 |
| `POST_NOTIFICATIONS` | 在Android 13+ 上显示通知 |
| `health.READ_STEPS` | 连接Health Connect服务读取步数 |
| `health.WRITE_STEPS` | 连接Health Connect服务写入步数 |
| `BODY_SENSORS` | 访问身体传感器数据 |

## 📂 应用模块结构

```
app/
├── src/
│   ├── main/
│   │   ├── java/com/example/stepstracking/
│   │   │   ├── MainActivity.kt             # 主界面，显示当前步数和统计
│   │   │   ├── HealthStepsActivity.kt      # Health Connect集成界面
│   │   │   ├── StepsRepository.kt          # 数据仓库，管理步数数据
│   │   │   ├── StepsTrackingService.kt     # 前台服务，记录和更新步数
│   │   │   ├── SplashActivity.kt           # 启动页面和权限处理
│   │   │   ├── MainViewModel.kt            # 主视图模型，处理UI逻辑
│   │   │   └── ...
│   │   └── res/                            # 资源文件目录
│   └── ...
└── ...
```

## 🚀 构建与安装

1. 使用Android Studio打开项目
2. 在`build.gradle.kts`文件中检查依赖版本
3. 连接Android设备或启动模拟器
4. 点击"运行"按钮构建并安装应用

> 💡 **提示**：首次运行应用时会请求必要权限，请务必允许以获得完整功能体验。
## 📱 应用屏幕截图

## ⚠️ 限制与已知问题

- 某些没有步数传感器的设备可能无法正常工作
- 后台步数统计在部分厂商的设备上可能受到系统优化的影响
- Health Connect功能需要设备安装Google的Health Connect应用

## ✅ 兼容性测试

应用已在以下环境进行测试：

| Android版本          | 状态 |
|--------------------| --- |
| Android 8.0 (Oreo) | ✅ 通过 |
| Android 9.0 (Pie)  | ✅ 通过 |
| Android 10         | ✅ 通过 |
| Android 11         | ✅ 通过 |
| Android 12         | ✅ 通过 |
| Android 13         | ✅ 通过 |
| Android 14         | ✅ 通过 |
| Android 15         | ✅ 通过 |
| Android 16         | ✅ 通过 |

## 👥 贡献与反馈

欢迎提交问题报告、功能建议或贡献代码。请通过GitHub Issues或Pull Requests参与项目改进。

## 📜 开源许可

该项目采用MIT许可证。详情请参见LICENSE文件。

## 📝 更新日志

### 版本 1.0 🎉
- 初始版本发布
- 实现基础步数跟踪功能
- 添加Health Connect集成
- 支持周数据统计

---

*Keep walking, stay healthy! 🚶‍♀️🚶‍♂️*

---

## Star History

[![Star History Chart](https://api.star-history.com/svg?repos=Pangu-Immortal/StepsTracking&type=Date)](https://star-history.com/#Pangu-Immortal/StepsTracking&Date)

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

1. Fork the repository
2. Create your feature branch (`git checkout -b feature/AmazingFeature`)
3. Commit your changes (`git commit -m 'Add some AmazingFeature'`)
4. Push to the branch (`git push origin feature/AmazingFeature`)
5. Open a Pull Request
## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.
