# EasyMcAdmin Plugin

> Modern, open-source Minecraft server management – easy and powerful.

## What is EasyMcAdmin?

EasyMcAdmin is a next-generation Minecraft server admin plugin  
for Bukkit/Spigot/Paper that lets you manage your server the modern way.  
Control players, permissions, console, and more – always securely, easily, and remotely.

## Features

- Advanced admin panel integration for Bukkit / Spigot / Paper servers
- Player management & moderation commands
- Full LuckPerms support for managing ranks/permissions
- Secure, real-time remote console access (RCON alternative)
- Simple configuration via `config.yml` (see below)
- Extensible with your own subcommands
- Fast, stable, and production-ready

## Installation

1. Download the latest plugin JAR from [Releases](https://github.com/hasirciogluhq/EasyMcAdmin/releases)
2. Place `EasyMcAdmin-plugin.jar` in your server's `plugins/` folder
3. Start your server to generate the config files
4. Set your token using the command `/<easymcadmin/ema> setToken <your-token-here>`
5. (Optional) Edit the config at `plugins/EasyMcAdmin/config.yml` if needed
6. Reload or restart your server to apply any manual config changes

## Configuration

Basic `config.yml` example:

```yaml
# Core configuration

server:
  id: "" # Automatically generated on first start, do not change
  token: "" # Set via in-game command ONLY

transport:
  enabled: true
  host: "localhost"
  port: 8798
```

- `server.id`: Unique server ID. Generated and managed automatically.
- `server.token`: Authentication token. **Set using the command `/easymcadmin setToken <token>` instead of editing manually.**
- `transport`: TCP communication config – defaults work out of the box.

**Note:**  
There are no other required configuration sections outside of the above. Database/web panel config is not needed in the plugin, only on the backend panel.

### How authentication works

- On first boot, a unique `server.id` is generated and stored if not present:
  ```java
  serverId = getConfig().getString("server.id", "");
  // If empty, generate and save to config
  ```
- You must set your authentication token via command (`/easymcadmin setToken`).  
  This securely writes the token to `config.yml` and triggers a connection to your backend or dashboard.

## Commands & Permissions

All plugin commands are handled under `/easymcadmin` or `/ema` , using a main command + subcommands system.

### Example Commands

| Command                         | Permission        | Description                           |
| ------------------------------- | ----------------- | ------------------------------------- |
| `/easymcadmin,ema help`             | easymcadmin.use   | Lists available EasyMcAdmin commands  |
| `/easymcadmin,ema setToken <token>` | easymcadmin.admin | Sets the backend authentication token |

#### `/easymcadmin setToken` usage

Sets the backend/dashboard authentication token for your server.

```
/easymcadmin setToken f2ab3c4d5e6f70123456789abcdef0123456789abcdef01
```

- **Token must be at least 32 characters**
- Updates the config and immediately attempts a secure connection
- Only users with `easymcadmin.admin` permission can use this command

#### Example Permission Setup (LuckPerms)

Give yourself admin rights:

```
/lp user YourName permission set easymcadmin.admin true
```

## Command System Overview

- **Main command:** `/easymcadmin`
  - Subcommands registered using a system like:
    ```java
    registerSubCommand("setToken", new SetTokenSubCommand(plugin));
    ```
- Each subcommand (`SetTokenSubCommand`, etc.) implements:
  ```java
  @Override
  public String getPermission() { return "easymcadmin.admin"; }
  ```
- The main command ensures that only users with permission can access each subcommand.

All config reading/writing (besides server.id/server.token and transport) is managed internally, so you don't need to add additional configuration.

## Usage

After setup and configuring your token, use `/easymcadmin help` for a list of features and commands.  
All subcommands provide usage info and permission checks automatically.

- **LuckPerms integration:**  
  All permission/group changes fully respect and work with LuckPerms.
- **Console output & event tracking**:  
  Managed automatically by the plugin backend logic, with no config needed.

## Contributing

Contributions are welcome!

- Open issues or request features on GitHub
- Submit Pull Requests for changes or enhancements

See [CONTRIBUTING.md](./.github/CONTRIBUTING.md) for project guidelines.

## License

This project is licensed under the **GNU Affero General Public License v3.0 (with Commercial Exception)**.  
See [LICENSE](./LICENSE) for more information.

## Branding & Credits

**EasyMcAdmin** is a trademark of HasirciogluHQ / PhineUp LLC.  
You may not use the name or logo without permission.

---

Thank you for choosing EasyMcAdmin!  
If you find it useful, please star the repository or contribute ✨
