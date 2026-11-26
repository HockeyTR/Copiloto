# Copiloto (app Android)

Asistente de voz para conducir. Escucha, responde con Claude y lee la respuesta en voz alta.
Abre Waze, Spotify, YouTube Music, WhatsApp y el marcador con la orden ya puesta.

## Cómo compilarla

1. Instala Android Studio (Koala o más reciente).
2. File > Open y elige esta carpeta. Deja que sincronice Gradle.
3. Conecta el móvil con depuración USB y pulsa Run.
4. En el primer arranque pega tu clave de la API de Anthropic (console.anthropic.com).

## Voz

Usa el motor de texto a voz de Android con tono grave (0.7) y velocidad 0.95. Para una voz más
natural, en Ajustes de Android > Texto a voz instala o elige otro motor (por ejemplo, el de Google)
y una voz masculina en español. Los valores están en `onInit` de `MainActivity.kt`.

## Límites

- Android Auto: esta app se usa con el móvil en el soporte. Android Auto solo admite en su pantalla
  apps de navegación, mensajería, música y algunas más, no asistentes propios.
- Seguridad: la clave se guarda en el móvil, sin cifrar. Para algo más que uso personal,
  pásala por un servidor tuyo.
- Con el micrófono abierto, el reconocimiento de voz de Android puede enviar audio a Google.
