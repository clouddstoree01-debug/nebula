# Nebula Limbo Rebirth v1.1.0 — Fabric Mod (Minecraft Java 26.3 / Java 25)

Mod Fabric **100% Akurat Sesuai Video 1 & Video 2** untuk **Minecraft Java Edition 26.3** (Unobfuscated Mojang Mappings, Fabric Loom 1.17, Java 25).

## Apa yang Direvisi di v1.1.0 (100% Identik dengan Kedua Video)?
1. **Kunci Otomatis Tombol `F5` Selama Cinematic (`MinecraftClientMixin` & `CameraMixin`)**
   - Selama fase **Cinematic Jatuh & Kedip 2x**, **Cinematic Orbit 360° di Nebula (00:00)**, dan **Cinematic Rebirth di Overworld (00:15)**, tombol **`F5` dikunci otomatis** (`keyTogglePerspective.consumeClick()`) dan status kamera `isDetached()` dipaksa aktif saat orbit sehingga tubuh 3D pemain asli dijamin selalu terlihat utuh di tengah pusaran partikel.
   - Saat sedang menunggu di dalam dimensi Limbo (sebelum timer mencapai `00:00`), pemain tetap bebas menggunakan `F5`.
   - Bisa diatur kapan saja dengan perintah `/hc limbo lock_f5 <true|false>`.
2. **Tekstur Langit Nebula Custom (`assets/nebula_limbo/textures/environment/nebula_sky.png`) + Partikel Gelap & Bintang Berlian**
   - Menyertakan tekstur langit kosmik *Nebula* (pusat cyan bercahaya `#68EAFF` dengan pusaran ungu-magenta `#6C228E`) serta kombinasi partikel bintang putih berlian (`END_ROD` + `FIREWORK`) dan partikel kluster gelap (`SQUID_INK` + `REVERSE_PORTAL`) persis seperti di *frame* 08–12 pada video.
3. **Fullscreen White Flash & Eyelid Blink di Atas Seluruh HUD (`HudElementRegistry.addLast`)**
   - Kilatan putih (`#FFFFFF`) pada detik `00:13–00:15` dan kedipan mata hitam kini merender di lapisan paling atas (`addLast`), menutupi *hotbar*, *heart bar*, *bossbar*, dan *chat* 100% identik dengan *frame* 14–15 di video.
4. **Transisi Zoom-In Kamera Halus dari 3rd-Person ke 1st-Person pada Detik 00:15–00:18**
   - Setelah teleport kembali ke Kasur (*Bed*) atau titik terakhir mati pada detik `00:15`, kamera memperlihatkan pilar cahaya vertikal (`END_ROD` + `SQUID_INK`), gelombang cincin *shockwave* melingkar di tanah, dan semburan `TOTEM_OF_UNDYING`, lalu **zoom-in secara mulus kembali ke sudut pandang First-Person** tepat saat *cinematic* selesai (persis seperti *frame* 16 hingga *frame* 19), lengkap dengan 3 ikon *buff* di pojok kanan atas (*Regeneration*, *Speed*, *Resistance*).
