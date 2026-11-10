# Anki Video Player — Android

Player Android em Kotlin / Compose / Media3 para estudar idiomas com vídeos e legendas.

## Versão 0.1.1

- Vídeos locais (formatos aceitos pelo Android/Media3) e URLs diretas de vídeo (MP4, HLS e DASH quando suportados pelo servidor e dispositivo).
- Legendas externas SRT/VTT/ASS (ASS básico); palavras selecionáveis num painel sincronizado; velocidade, repetição e ajuste de sincronia.
- **Principal:** favoritar uma legenda, com nome do filme/série, temporada e episódio, texto completo e timestamps originais + timestamps de reprodução.
- Favoritos armazenados localmente, persistentes entre aberturas, com lista e botão remover. Não cria cartão ao favoritar.
- **Exportar TXT UTF-8** para leitura humana, agrupado por obra/temporada/episódio.
- **Exportar JSON** estruturado (versão 1) e importar backup JSON, ignorando duplicatas.
- **Opcional:** tocar numa palavra para copiar o prompt de dicionário contextualizado, colar JSON gerado manualmente e compartilhar os dados como texto com AnkiDroid. A criação de cartão requer confirmação no AnkiDroid.
- **Sem integração de API, sem chave de API, sem Gemini integrado.**

### Instalação

Baixe o arquivo APK da seção [Releases](https://github.com/oyayasu/anki-video-playet/releases). Necessário Android 8.0 ou superior.

**Atenção:** as builds de teste são assinadas com certificados de depuração efêmeros e podem exigir desinstalar uma versão anterior. Exporte seu JSON **antes de desinstalar** para não perder favoritas; depois importe o backup.

### Build

JDK 17, Android SDK 36 e Gradle 8.11.1:

```sh
gradle :app:testDebugUnitTest :app:assembleDebug
```

O [workflow Android APK](https://github.com/oyayasu/anki-video-playet/actions) cria o APK e publica a prévia na página Releases somente quando o job conclui com sucesso.

### Limitações

- Streaming online restrito a **URLs diretas de mídia** (sem DRM, autenticação ou extração de sites).
- A interação por palavras e o favorito usam o arquivo de legendas externo. Legendas embutidas podem aparecer no vídeo, mas não são acessíveis no painel nesta versão.
- A integração com AnkiDroid é via compartilhamento de texto, não criação automática no baralho; áudio e captura de imagem ainda não são adicionados ao cartão.
- As informações favoritas permanecem apenas no dispositivo, com exportação/importação para transferência ao PC.
