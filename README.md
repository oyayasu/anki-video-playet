# Anki Video Player — Android

Player em Kotlin, Jetpack Compose e Media3.

## Preview v0.2

- Reprodução de vídeos locais e URLs diretas, HLS e DASH quando suportados pelo aparelho.
- Compatibilidade com o menu **player externo** do Stremio via `ACTION_VIEW` Android (`http`, `https`, `content`, `file`; MIME `video/*`). Aceita posição de início quando fornecida e retorna a posição quando fechado.
- **Faixas de legendas embutidas:** detecta e permite escolher idiomas disponíveis (MKV/MP4/streams compatíveis), lê texto com `onCues` e exibe palavras selecionáveis, sem precisar importar um SRT separado.
- **Legendas externas opcionais:** SRT, VTT e ASS básico. Quando há ambas, você pode escolher a fonte.
- Interface inspirada no MX Player: controles sobre o vídeo, tela cheia horizontal, barra de progresso, avanço/retrocesso de 10s (inclusive duplo toque), gesto horizontal para buscar e gestos verticais para volume/brilho, zoom e velocidade.
- Favoritas salvas localmente com obra, temporada/episódio, fala e timestamps; exportação TXT e JSON; importação de backup.
- Criação de cartão **opcional** por prompt copiado + JSON colado, com compartilhamento de texto para AnkiDroid. **Sem API de IA**.

### Limitações

- O Stremio precisa enviar uma URL de mídia ou URI de conteúdo compatível. Não contornamos DRM nem reproduzimos sites como páginas web.
- Faixas de legendas em formato **imagem** (PGS/VobSub) não são texto selecionável. Faixas não compatíveis com Media3 também não estarão disponíveis.
- O Media3 informa o texto das legendas embutidas, mas não fornece o instante exato de término de cada fala via `onCues`. Neste preview o intervalo salvo para uma fala embutida é uma estimativa inicial de 2,5 segundos. SRT externo conserva seus tempos originais.
- Metadados de título, temporada e episódio podem não vir do Stremio. Edite-os na seção de favoritas.
- APK assinado para depuração no GitHub Actions. **Exporte seu JSON antes de desinstalar** uma build, pois os dados locais serão apagados.

### Instalar e compilar

Instale pela página [Releases](https://github.com/oyayasu/anki-video-playet/releases). Em Stremio, ative o uso de um player externo e escolha *Anki Video Player*.

Requer Android 8+, JDK 17 e Android SDK 36 para compilar:

```sh
gradle :app:testDebugUnitTest :app:assembleDebug
```
