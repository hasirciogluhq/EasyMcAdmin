<p align="center">
  <img src="https://user-images.githubusercontent.com/937328/272673824-bcf0cf86-8eec-4091-88fa-2b78652a2747.png" width="180" alt="EasyMcAdmin Logo" /><br/>
</p>

<h1 align="center">🚀 EasyMcAdmin Plugin</h1>
<p align="center">
  <i>Modern, open-source Minecraft server management – <b>easy</b>, <b>powerful</b>, <b>beautiful</b>.</i>
  <br><br>
  <img src="https://img.shields.io/github/v/release/hasirciogluhq/EasyMcAdmin?style=flat-square" alt="Release">
  <img src="https://img.shields.io/badge/java-17%2B-blue?style=flat-square" alt="Java">
  <img src="https://img.shields.io/badge/gradle-8.5%2B-informational?style=flat-square" alt="Gradle">
  <img src="https://img.shields.io/github/license/hasirciogluhq/EasyMcAdmin?style=flat-square" alt="License">
</p>

---

## 🧩 What is EasyMcAdmin?

✨ <b>EasyMcAdmin</b> is a <b>next-generation</b> Minecraft server admin plugin for Bukkit / Spigot / Paper that lets you manage your server in a <b>modern</b> & <b>secure</b> way.<br>
Control 👤 players, 🔐 permissions, 🖥️ console, and more – easily, securely, and remotely.

---

## 🚦 Features

- 🛡️ <b>Seamless admin panel integration</b> for Bukkit / Spigot / Paper
- 👮 <b>Player management</b> & moderation commands
- 🪪 <b>LuckPerms support</b> for managing ranks / permissions
- 🔐 <b>Real-time, secure remote console access</b> (RCON alternative)
- ⚙️ <b>Easy configuration</b> via <code>config.yml</code>
- 🧩 <b>Extensible</b> command system (make your own subcommands)
- 💡 <b>Fast</b>, <b>stable</b> and <b>production-ready</b>

---

## 📦 Installation <sup><kbd>User</kbd></sup>

<ol>
  <li>⬇️ Download the latest plugin JAR from <a href="https://github.com/hasirciogluhq/EasyMcAdmin/releases"><b>Releases</b></a></li>
  <li>📁 Place <code>EasyMcAdmin-plugin.jar</code> in your server's <code>plugins/</code> folder</li>
  <li>🚀 Start your server to generate the config files</li>
  <li>🔑 Set your token using:<br/><code>/<easymcadmin/ema> setToken &lt;your-token-here&gt;</code></li>
  <li>📝 (Optional) Edit <code>plugins/EasyMcAdmin/config.yml</code> if needed</li>
  <li>🔁 Reload or restart your server to apply changes</li>
</ol>

---

## 🛠️ Build & Install <sup><kbd>Source</kbd></sup>

Want to build from source? Use our slick scripts and Gradle setup for instant results!

### 🔑 Prerequisites

- ☕ <b>Java 17</b> (required)
- ⚙️ <b>Gradle 8.5+</b> (wrapper included, skip install)

### ⚡ Quick Install (macOS/Linux)

```bash
# 🌀 Clone the repo
git clone https://github.com/hasirciogluhq/EasyMcAdmin.git
cd EasyMcAdmin/apps/mc-plugin

# 🏗️ Build & deploy (auto-detects Java 17)
./build.sh
```

> 🪄 <b>What <code>build.sh</code> does:</b>
> - Finds and uses <b>Java 17+</b>
> - 💻 Builds via Gradle
> - 🚚 Deploys the JAR to your <b>test server plugins</b> folder (see <code>deploy</code> in <i>build.gradle.kts</i>)

> <img src="https://img.icons8.com/color/32/000000/folder-invoices.png" style="vertical-align:middle;margin-right:2px" width="18"/> <b>Default deploy path:</b>
> ```
> /Users/hasircioglu/mc-server-1/servers/test/plugins
> ```
> <br>💡 <i>Change this to match your own server folder!<br>Edit the target line in <code>deploy</code> inside <a href="./build.gradle.kts"><code>build.gradle.kts</code></a>.</i>

### 🖐️ Manual Build Steps

Prefer Gradle directly?

```bash
# (Optional) Make sure JAVA_HOME is Java 17
export JAVA_HOME=$(/usr/libexec/java_home -v17)
./gradlew build       # 👷 Build plugin JAR (at build/libs/)
./gradlew deploy      # 🚀 Deploy as above
```

---

## 🎮 Example Usage

<ol>
  <li>🟢 Start the Minecraft server</li>
  <li>🔑 Set your panel/server token:<br>
    <code>/easymcadmin setToken &lt;your-token&gt;</code><br>
    <sub><b>Example:</b></sub>
    <pre>
/easymcadmin setToken f2ab3c4d5e6f70123456789abcdef0123456789abcdef01
    </pre>
  </li>
  <li>🔗 Confirm backend connection in console/in-game</li>
  <li>💡 Use <code>/easymcadmin help</code> for all commands</li>
</ol>

---

## ⚙️ Configuration

<details>
<summary>🔽 <b>Example <code>config.yml</code></b> <small>(click to expand)</small></summary>

```yaml
# Core configuration
server:
  id: ""       # Auto-generated on first start, do not edit
  token: ""    # Set via in-game command ONLY
transport:
  enabled: true
  host: "localhost"
  port: 8798
```
</details>

<ul>
  <li><b>server.id</b>: 🔗 Unique server ID (auto-generated)</li>
  <li><b>server.token</b>: 🔑 Auth token, <i>set using the command</i></li>
  <li><b>transport:</b>  📡 TCP config (defaults work out of the box)</li>
</ul>

No SQL or web config needed; just install the plugin and it's ready to use!

### How authentication works

- On the first start, a unique server id is generated automatically:
  ```
  serverId = getConfig().getString("server.id", "");
  // If empty, generate and save
  ```
- Set your token only using the `/easymcadmin setToken` command in-game (do not edit manually!)


## ⌨️ Commands & Permissions

All actions use <strong><code>/easymcadmin</code></strong> (or <strong><code>/ema</code></strong>) plus subcommands.

### 📝 Command Reference

| 🕹️ <b>Command</b>                      | 📄 <b>Permission</b>      | ℹ️ <b>Description</b>                |
| --------------------------------------- | ------------------------- | ------------------------------------ |
| `/easymcadmin,ema help`                 | easymcadmin.use           | List all EasyMcAdmin commands        |
| `/easymcadmin,ema setToken <token>`     | easymcadmin.admin         | Set backend authentication token     |

#### 🔐 `/easymcadmin setToken` usage

- <b>Token must be at least 32 chars</b>
- Updates config & attempts connection instantly
- <b>Only</b> users with <code>easymcadmin.admin</code> may run it

#### 🥇 Example LuckPerms Setup

```bash
/lp user YourName permission set easymcadmin.admin true
```

---

## 🧠 Command System Overview

- Ana komut: `/easymcadmin`
- Alt komutlar şu şekilde eklenir:
  ```
  registerSubCommand("setToken", new SetTokenSubCommand(plugin));
  ```
- Her alt komut için izin tanımlanabilir:
  ```java
  @Override
  public String getPermission() { return "easymcadmin.admin"; }
  ```
- Ana komut otomatik olarak izinleri kontrol eder.
- Tüm yapılandırma eklenti tarafından yönetilir; sadece token'ı girmeniz yeterlidir!

---

## 💎 Usage Highlights

- ✨ After initial setup, use <code>/easymcadmin help</code> to see everything!
- 🏆 <b>LuckPerms integration</b> – permission/rank changes are seamless.
- 🖥️ <b>Console output, logs, event tracking</b> – handled automatically!

---

## 🤝 Contributing

<img src="https://img.shields.io/github/issues/hasirciogluhq/EasyMcAdmin?style=flat-square" alt="Issues"/>&nbsp;
<img src="https://img.shields.io/github/pulls/hasirciogluhq/EasyMcAdmin?style=flat-square" alt="Pull Requests"/>

Pull requests & feature ideas are always welcome!  
See [CONTRIBUTING.md](./.github/CONTRIBUTING.md) for guidelines.

---

## ⚖️ License

📝 Licensed under <b>GNU Affero GPL v3.0 (with Commercial Exception)</b><br>
See [LICENSE](./LICENSE) for full details.

---

## 🖌️ Branding & Credits

<b>EasyMcAdmin</b> is a trademark of HasirciogluHQ / PhineUp LLC.<br>
You may not use the name or logo without permission.

---

<p align="center">
  <b>Thank you for choosing EasyMcAdmin!</b><br>
  <img src="https://img.shields.io/github/stars/hasirciogluhq/EasyMcAdmin?style=social" alt="GitHub stars"/>
  <br>
  <i>If you like it, ⭐️ star or contribute!</i>
</p>

