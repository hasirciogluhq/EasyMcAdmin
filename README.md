# Easy MC Admin Plugin

Universal Minecraft Server Management Plugin for Bukkit/Spigot servers.

## Özellikler

- Universal plugin - Tüm Minecraft versiyonlarında çalışır
- Bukkit/Spigot API desteği
- Kolay yapılandırma
- Web panel entegrasyonu için hazır

## Gereksinimler

- Java 17 veya üzeri
- Gradle 8.5 veya üzeri
- Minecraft Server (Bukkit/Spigot/Paper)

## Kurulum

### Geliştirme Ortamı

1. Projeyi klonlayın:
```bash
git clone https://github.com/hasirciogluhq/mc-admin.git
cd mc-admin/apps/mc-plugin
```

2. Gradle wrapper'ı çalıştırın (ilk kez):
```bash
./gradlew wrapper
```

3. Projeyi build edin:
```bash
./gradlew build
```

4. Plugin JAR dosyası `build/libs/EasyMcAdmin.jar` konumunda oluşturulacaktır.

### Sunucuya Kurulum

1. Build edilen JAR dosyasını sunucunuzun `plugins/` klasörüne kopyalayın.
2. Sunucuyu yeniden başlatın veya `/reload` komutunu kullanın.
3. Plugin otomatik olarak `plugins/EasyMCAdmin/config.yml` dosyasını oluşturacaktır.

## Yapılandırma

Plugin yapılandırması `plugins/EasyMCAdmin/config.yml` dosyasından yapılabilir.

## Komutlar

- `/easymcadmin` veya `/ema` veya `/mcadmin` - Ana plugin komutu

## İzinler

- `easymcadmin.use` - Temel kullanım izni
- `easymcadmin.admin` - Yönetici izinleri
- `easymcadmin.*` - Tüm izinler (varsayılan: op)

## Geliştirme

### Proje Yapısı

```
mc-plugin/
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/hasirciogluhq/easymcadmin/
│   │   │       └── EasyMcAdmin.java
│   │   └── resources/
│   │       ├── plugin.yml
│   │       └── config.yml
├── build.gradle.kts
├── settings.gradle.kts
└── README.md
```

### Build Komutları

- `./gradlew build` - Projeyi build eder
- `./gradlew shadowJar` - Shadow JAR oluşturur (bağımlılıklarla birlikte)
- `./gradlew clean` - Build klasörünü temizler

## Lisans

Bu proje özel bir projedir.

## Yazar

hasirciogluhq - https://github.com/hasirciogluhq

