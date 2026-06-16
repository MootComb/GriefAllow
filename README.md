<h1 align="center">GriefAllow</h1>

<p align="center">
  <img src="https://img.shields.io/badge/Minecraft-1.13%2B-brightgreen" alt="Minecraft Version">
  <img src="https://img.shields.io/badge/Java-8%2B-orange" alt="Java Version">
  <img src="https://img.shields.io/badge/License-GPL%203.0-blue" alt="License">
  <img src="https://img.shields.io/badge/Folia-Supported-purple" alt="Folia">
</p>

**A powerful Minecraft plugin that selectively enables griefing mechanics in specific worlds and regions.** Perfect for anarchy servers, faction servers, or any server that needs controlled griefing zones.

---

## Official Downloads

> [!WARNING]
> **Download only from these official sources.** Other sites are not affiliated with this project and may contain malicious code.

- **Modrinth:** [https://modrinth.com/plugin/griefallow](https://modrinth.com/plugin/griefallow)
- **SpigotMC:** [https://www.spigotmc.org/resources/griefallow.113022](https://www.spigotmc.org/resources/griefallow.113022)

---

## Overview

This plugin is designed for servers that allow griefing. Its primary purpose is to enable and control griefing within designated territories. Whether you're running an anarchy server, a faction server, or just want to create specific griefing zones, GriefAllow gives you full control.

---

## Key Features

- **Full plugin configuration** – Customize everything to your needs
- **Selective griefing** – Enable griefing by world or region
- **Region plugin support** – Compatible with WorldGuard, RedProtect, and other region plugins
- **Folia support** – Fully compatible with Folia and Paper servers
- **Lightweight** – Minimal performance impact
- **Easy to use** – Simple configuration with clear documentation

---

## How to Compile

### Prerequisites
- **Java 8** or higher
- **Maven 3.6+** or higher

### Build Instructions

```bash
# Clone the repository
git clone https://github.com/MootComb/GriefAllow.git

# Navigate to the project directory
cd GriefAllow

# Build with Maven
mvn clean package
```

### Output
The compiled JAR file will be located in the `target/` directory as `GriefAllow-<version>.jar`.

### Installation
1. Copy the JAR file to your server's `plugins/` folder
2. Restart your server or use a plugin manager
3. Configure the `config.yml` file to your liking
4. Reload the config with `/griefallow reload`

---

## Quick Start

1. **Install the plugin** – Place the JAR in your `plugins/` folder
2. **Configure** – Edit `config.yml` to set your griefing rules
3. **Reload** – Use `/griefallow reload` to apply changes
4. **Enjoy** – Watch the chaos unfold!

---

## Support

For issues, suggestions, or questions:
- **GitHub Issues:** [https://github.com/MootComb/GriefAllow/issues](https://github.com/MootComb/GriefAllow/issues)

---

## License

This project is licensed under the **GNU General Public License v3.0** – see the [LICENSE](LICENSE) file for details.
