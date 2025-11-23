# EasyMcAdmin

> A powerful, modular admin panel toolkit for Minecraft servers — built for developers, admins and communities.

## 🚀 What is EasyMcAdmin?

EasyMcAdmin is an extensible admin panel and plugin suite crafted to simplify server management for Minecraft.  
Whether you're running a public server with thousands of players or a niche private community, EasyMcAdmin gives you the tools to build dashboards, manage players, integrate permissions, handle RCON commands and much more — all under one unified roof.

## 🔧 Features

- Modular plugin architecture (works with Bukkit/Spigot/Paper)  
- Player & permissions management (LuckPerms support)  
- Real-time RCON / remote console support  
- Dashboard UI (web) for staff & moderators  
- Event hooks, custom commands and extension points  
- Designed for scalability (microservices friendly, monorepo architecture)  
- Plugin history and change tracking preserved  

## 🧱 Architecture Overview

```

/
├─ apps/
│   ├─ plugin/        ← Minecraft plugin source
│   └─ dashboard/     ← Web admin panel (Next.js / TS)
├─ packages/
│   └─ shared/        ← Shared utilities, types, services
├─ infra/
│   └─ k8s/          ← Kubernetes manifests & deployment configs
└─ README.md

````

This monorepo structure allows you to share code, maintain versioned dependencies and deploy each part independently.

## 📦 Installation

### Plugin (Minecraft server)
1. Build the plugin: `./gradlew :apps:plugin:build`  
2. Copy `build/libs/EasyMcAdmin-plugin.jar` to your server’s `plugins/` directory  
3. Restart the server  
4. Configure `config.yml` under `plugins/EasyMcAdmin` folder  

### Dashboard (Web Panel)
1. `cd apps/dashboard`  
2. Install dependencies: `npm install`  
3. Configure `.env` (for example `MONGO_URI`, `SESSION_SECRET`, `RCON_HOST`, `RCON_PORT`)  
4. Run dev: `npm run dev` or build for production: `npm run build && npm run start`  

## 🛠 Configuration

Configure the plugin by editing `plugins/EasyMcAdmin/config.yml`. Example key settings:

```yaml
database:
  host: localhost
  port: 27017
  name: easy_mca

rcon:
  host: 127.0.0.1
  port: 25575
  password: yourpassword

dashboard:
  enabled: true
  url: http://your-panel.domain
````

## 🧑‍🤝‍🧑 Contribution

We welcome contributions! Please follow these steps:

1. Fork the repo
2. Create a branch: `git checkout -b feature/my-awesome-feature`
3. Make your changes (code, docs, tests)
4. Run tests and verify everything works
5. Submit a Pull Request
6. One of the maintainers will review your changes

Please adhere to the coding standards (TypeScript / Go / Java) and the architecture guidelines.

## 🎯 Roadmap

* [ ] Dashboard plugin marketplace
* [ ] Webhooks for third-party integrations
* [ ] Multi-server clustering support
* [ ] Analytics & live player metrics
* [ ] Official documentation site

## 📄 License

This project is licensed under the **GNU Affero General Public License v3.0** with an additional Commercial Exception — see [LICENSE](LICENSE) file for full details.

## 🧾 Branding & Trademark

The name **EasyMcAdmin** and associated logos are trademarks of HasirciogluHQ / PhineUp LLC. No unauthorized use without written permission.

---

Thank you for using EasyMcAdmin. Let’s build the next-gen Minecraft admin ecosystem together!