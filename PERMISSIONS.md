# Peek Mod - 权限配置

本文档描述了Peek模组的权限节点系统。所有权限节点都以 `peek.` 为前缀。

## 权限节点结构

### 基础命令权限 (peek.command.*)

所有基础用户命令，默认权限等级为 0（所有玩家）：

- `peek.command.peek` - 发送窥视请求 (/peek player \<target>)
- `peek.command.accept` - 接受窥视请求 (/peek accept)  
- `peek.command.deny` - 拒绝窥视请求 (/peek deny)
- `peek.command.stop` - 停止当前窥视 (/peek stop)
- `peek.command.settings` - 访问设置菜单 (/peek settings)
- `peek.command.blacklist` - 管理黑名单 (/peek settings blacklist)
- `peek.command.stats` - 查看统计信息 (/peek stats)

### 管理员权限 (peek.admin.*)

管理员命令，默认权限等级为 2-3：

- `peek.admin` - 访问管理员命令面板 (/peekadmin) [权限等级: 2]
- `peek.admin.stats` - 查看全局统计 (/peekadmin stats) [权限等级: 2]
- `peek.admin.top` - 查看排行榜 (/peekadmin top) [权限等级: 2]
- `peek.admin.list` - 查看玩家列表 (/peekadmin list) [权限等级: 2]
- `peek.admin.player` - 查看玩家详情 (/peekadmin player) [权限等级: 2]
- `peek.admin.sessions` - 查看活跃会话 (/peekadmin sessions) [权限等级: 2]
- `peek.admin.cleanup` - 执行数据清理 (/peekadmin cleanup) [权限等级: 3]
- `peek.admin.force_stop` - 强制停止会话 (/peekadmin force-stop) [权限等级: 3]

### 豁免权限 (peek.bypass.*)

这些权限允许玩家绕过特定限制，默认权限等级为 2（OP玩家）：

- `peek.bypass.cooldown` - 绕过冷却时间限制
- `peek.bypass.distance` - 绕过距离限制  
- `peek.bypass.time_limit` - 绕过会话时间限制
- `peek.bypass.max_sessions` - 绕过最大并发会话限制
- `peek.bypass.private_mode` - 绕过玩家的私有模式设置
- `peek.bypass.blacklist` - 绕过玩家的黑名单限制
- `peek.bypass.dimension` - 绕过跨维度限制
- `peek.bypass.movement` - 绕过静止状态检查

## 权限管理方式

### 1. 使用权限管理插件 (推荐)

如果服务器安装了权限管理插件（如LuckPerms），可以精确控制每个权限节点：

```yaml
# 给玩家基础窥视权限
lp user <username> permission set peek.command.peek true

# 给玩家管理员权限
lp user <username> permission set peek.admin true

# 禁止玩家使用某个功能
lp user <username> permission set peek.command.blacklist false
```

### 2. 使用原版权限等级

如果没有权限插件，系统会回退到原版权限等级：

- **等级 0**: 所有玩家 - 可使用基础窥视功能
- **等级 2**: OP玩家 - 可使用大部分管理员功能  
- **等级 3**: 高级管理员 - 可使用所有功能，包括清理和强制停止

### 3. 权限继承

权限系统支持层级继承：

- `peek.admin` 权限自动包含所有 `peek.admin.*` 子权限
- `peek.command` 权限自动包含所有 `peek.command.*` 子权限

### LuckPerms 组配置示例

```yaml
# 普通用户组
group.default:
  permissions:
    - peek.command.peek
    - peek.command.accept  
    - peek.command.deny
    - peek.command.stop
    - peek.command.stats

# VIP用户组  
group.vip:
  parent: default
  permissions:
    - peek.command.settings
    - peek.command.blacklist

# 管理员组
group.admin:
  parent: vip
  permissions:
    - peek.admin.stats
    - peek.admin.top
    - peek.admin.list
    - peek.admin.player
    - peek.admin.sessions

# 超级管理员组
group.superadmin:
  parent: admin
  permissions:
    - peek.admin.cleanup
    - peek.admin.force_stop
    - peek.bypass.cooldown
    - peek.bypass.distance
    - peek.bypass.time_limit
    - peek.bypass.max_sessions
    - peek.bypass.private_mode
    - peek.bypass.blacklist
    - peek.bypass.dimension
    - peek.bypass.movement
```

## 故障排除

### 常见问题

1. **"You don't have permission to use this command"**
   - 检查用户是否有对应的权限节点
   - 确认权限插件配置正确

2. **命令不显示在TAB补全中**
   - 用户没有执行该命令的权限
   - 这是正常行为，有助于隐藏用户无法使用的命令

3. **管理员命令无法使用**
   - 确认用户有 `peek.admin` 权限或对应的子权限
   - 检查权限等级设置

### 调试命令

```bash
# 检查玩家权限 (LuckPerms)
/lp user <username> permission check peek.command.peek

# 查看玩家所有权限
/lp user <username> permission info
```