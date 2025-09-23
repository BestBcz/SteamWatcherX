
# SteamWatcherX

视奸你的群友-SteamWatcherX 是一个 mirai 插件，可以订阅群友的Steam状态并将状态变更发送到指定qq群聊

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

/bind
/unbind
/list

## 配置（Config）

# Steam API Key
apiKey: 
# 状态检查间隔 (毫秒), 修改后需重载插件
interval: 60000
# 是否开启在线状态通知
notifyOnline: false
# 是否开启游戏状态通知
notifyGame: true
# 是否开启成就解锁通知
notifyAchievement: true