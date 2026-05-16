
# SteamWatcherX

[![MiraiForum](https://img.shields.io/badge/Forum-Mirai?style=flat-square&label=Mirai
)](https://mirai.mamoe.net/topic/2831/steamwatcherx-%E8%A7%86%E5%A5%B8%E7%BE%A4%E5%8F%8B%E7%9A%84steam%E7%8A%B6%E6%80%81%E5%B9%B6%E5%B0%86%E7%8A%B6%E6%80%81%E5%8F%98%E6%9B%B4%E5%8F%91%E9%80%81%E5%88%B0%E6%8C%87%E5%AE%9Aqq%E7%BE%A4-steam%E8%A7%86%E5%A5%B8%E5%99%A8?_=1760916893487)

视奸你的群友-SteamWatcherX 是一个 mirai 插件，可以订阅群友的Steam状态并将状态变更发送到指定qq群聊

_仓库正在积极维护更新中，喜欢的话可以点个免费的star⭐支持我_

---

## 功能（Features）

- 可以订阅一个或多个 Steam 用户的状态（例如：上线、离线、游戏中，成就获取）。
- 当状态发生变化时，自动向指定的 QQ 群或 mirai 群聊发送通知。
- 支持简单配置，易于部署和使用。

---

## 安装（Installation）

1. 前往Release下载插件本体
2. 将插件放入mirai控制台的/plugins文件夹中
3. 重启控制台

## 使用方法（How to Use）

- 确保插件正确加载（查看控制台输出）
- 前往配置文件配置steam api key https://steamcommunity.com/dev/apikey
- 重启控制台
- 在你想要获取通知的群聊中发送/bind Steam64位ID(例如76561198377324521)
- 可以通过配置Config来修改部分功能

## 指令（Command）


SteamWatcherX 指令列表: 
```
/sw bind [SteamID] - 绑定 Steam 账号
/sw unbind [SteamID] - 解绑 Steam 账号 (不填ID则解绑所有)
/sw list - 查看本群所有绑定
/sw help - 显示此帮助信息
```

## 配置（Config）

- Steam API Key
apiKey: 
- 状态检查间隔 (毫秒), 修改后需重载插件
interval: 60000
- 是否开启在线状态通知
notifyOnline: false
- 是否开启游戏状态通知
notifyGame: true
- 是否开启成就解锁通知
notifyAchievement: true

## 插件图片

<img width="451" height="528" alt="image" src="https://github.com/user-attachments/assets/72e8ed23-cf4e-4449-8b57-e7c8542783ed" />


<img width="393" height="170" alt="image" src="https://github.com/user-attachments/assets/8840c302-dc4a-47dd-9d5f-c74e4941926d" />


<img width="414" height="177" alt="image" src="https://github.com/user-attachments/assets/f003b4da-3665-454c-9cdc-92841d9495f6" />

<img width="300" height="90" alt="rare-achievement" src="https://github.com/user-attachments/assets/7ffd9d9f-86e9-42e7-835b-b96aa5356b9f" />





