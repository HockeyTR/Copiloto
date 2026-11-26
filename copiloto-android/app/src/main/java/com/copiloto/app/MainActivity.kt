package com.copiloto.app

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.util.TypedValue
import android.view.Gravity
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.Executors

class MainActivity : Activity(), TextToSpeech.OnInitListener {

    // Modelo rápido para respuestas de voz. Cámbialo si quieres otro.
    private val model = "claude-haiku-5-5"

    private val system = """
        Eres Copiloto, un asistente de voz para una persona que conduce. Tu carácter es el de un copiloto
        de coche futurista: sereno, muy educado, preciso, leal y con un toque sutil de ironía, sin exagerar.
        Respondes siempre en español de España, con frases cortas y naturales, máximo 3 frases, sin listas,
        sin markdown, sin emojis ni símbolos, porque tu respuesta se lee en voz alta.
        No tienes acceso a tráfico, hora ni ubicación en directo: si te lo piden, dilo en una frase.
        Si la persona parece cansada, recomienda parar en un lugar seguro.
        Puedes abrir apps del móvil. Campo app: navegar (ir a un sitio, buscar un lugar, aparcar; q = destino),
        musica (Spotify; q = lo que quiera oír), youtube (YouTube Music; q = búsqueda),
        mensaje (WhatsApp; q = texto del mensaje), llamar (marcador; q = número, solo si lo ha dicho),
        o cadena vacía si no hace falta ninguna.
        Devuelve SOLO un JSON válido: {"voz":"texto a leer","app":"navegar|musica|youtube|mensaje|llamar|","q":"..."}
    """.trimIndent()

    private lateinit var tts: TextToSpeech
    private var ttsReady = false
    private var recognizer: SpeechRecognizer? = null
    private val history = JSONArray()
    private val io = Executors.newSingleThreadExecutor()

    private lateinit var statusView: TextView
    private lateinit var youView: TextView
    private lateinit var replyView: TextView
    private lateinit var micButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        tts = TextToSpeech(this, this)
        buildUi()
        if (apiKey().isBlank()) askForKey()
    }

    // ---------- Interfaz ----------

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0B1014"))
            setPadding(dp(20), dp(28), dp(20), dp(20))
        }
        statusView = TextView(this).apply {
            text = "COPILOTO · LISTO"
            setTextColor(Color.parseColor("#35D0B6"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
            letterSpacing = 0.1f
        }
        youView = TextView(this).apply {
            setTextColor(Color.parseColor("#9DB0C0"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            setPadding(0, dp(16), 0, dp(8))
        }
        replyView = TextView(this).apply {
            text = "Buenas. Toca el botón y dime qué necesitas."
            setTextColor(Color.parseColor("#EEF3F7"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 28f)
            typeface = Typeface.create("sans-serif-condensed", Typeface.NORMAL)
        }
        val scroll = ScrollView(this).apply {
            addView(replyView)
        }
        micButton = Button(this).apply {
            text = "HABLAR"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 26f)
            setTextColor(Color.parseColor("#04201B"))
            setBackgroundColor(Color.parseColor("#35D0B6"))
            setOnClickListener { listen() }
        }
        val stop = Button(this).apply {
            text = "CALLAR"
            setOnClickListener { if (ttsReady) tts.stop() }
        }
        val key = Button(this).apply {
            text = "CLAVE API"
            setOnClickListener { askForKey() }
        }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            addView(stop, LinearLayout.LayoutParams(0, dp(56), 1f))
            addView(key, LinearLayout.LayoutParams(0, dp(56), 1f))
        }
        root.addView(statusView)
        root.addView(youView)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(micButton, LinearLayout.LayoutParams(-1, dp(120)).apply { bottomMargin = dp(12) })
        root.addView(row)
        setContentView(root)
    }

    // ---------- Clave API ----------

    private fun prefs() = getSharedPreferences("copiloto", Context.MODE_PRIVATE)
    private fun apiKey() = prefs().getString("key", "") ?: ""

    private fun askForKey() {
        val input = EditText(this).apply { hint = "sk-ant-..."; setText(apiKey()) }
        AlertDialog.Builder(this)
            .setTitle("Clave de la API de Anthropic")
            .setMessage("Se guarda solo en este móvil.")
            .setView(input)
            .setPositiveButton("Guardar") { _, _ -> prefs().edit().putString("key", input.text.toString().trim()).apply() }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    // ---------- Voz de salida ----------

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) return
        tts.language = Locale("es", "ES")
        // Voz grave y pausada. En Ajustes de Android > Texto a voz puedes elegir otro motor o voz.
        tts.setPitch(0.7f)
        tts.setSpeechRate(0.95f)
        ttsReady = true
    }

    private fun speak(text: String) {
        runOnUiThread {
            replyView.text = text
            if (ttsReady) tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "copiloto")
        }
    }

    // ---------- Voz de entrada ----------

    private fun listen() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 1)
            return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            speak("Este móvil no tiene reconocimiento de voz disponible.")
            return
        }
        if (ttsReady) tts.stop()
        recognizer?.destroy()
        recognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(p: Bundle?) { setStatus("TE ESCUCHO") }
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(v: Float) {}
                override fun onBufferReceived(b: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onEvent(t: Int, p: Bundle?) {}
                override fun onPartialResults(p: Bundle?) {
                    val t = p?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                    if (t != null) runOnUiThread { youView.text = "Tú: $t" }
                }
                override fun onError(e: Int) {
                    setStatus("LISTO")
                    speak("No te he oído. Toca y repite.")
                }
                override fun onResults(r: Bundle?) {
                    val t = r?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                    if (t.isNullOrBlank()) { setStatus("LISTO"); return }
                    runOnUiThread { youView.text = "Tú: $t" }
                    ask(t)
                }
            })
            startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-ES")
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            })
        }
    }

    override fun onRequestPermissionsResult(code: Int, perms: Array<out String>, res: IntArray) {
        super.onRequestPermissionsResult(code, perms, res)
        if (code == 1 && res.firstOrNull() == PackageManager.PERMISSION_GRANTED) listen()
        else speak("Sin micrófono no puedo escucharte.")
    }

    private fun setStatus(s: String) = runOnUiThread { statusView.text = "COPILOTO · $s" }

    // ---------- Claude ----------

    private fun ask(text: String) {
        setStatus("PENSANDO")
        history.put(JSONObject().put("role", "user").put("content", text))
        io.execute {
            try {
                val raw = callClaude()
                val clean = raw.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
                var voz = clean
                var app = ""
                var q = ""
                try {
                    val j = JSONObject(clean)
                    voz = j.optString("voz")
                    app = j.optString("app")
                    q = j.optString("q")
                } catch (_: Exception) {}
                if (voz.isBlank()) voz = "No he entendido bien. ¿Puedes repetirlo?"
                history.put(JSONObject().put("role", "assistant").put("content", voz))
                while (history.length() > 12) history.remove(0)
                setStatus("LISTO")
                speak(voz)
                if (app.isNotBlank()) openApp(app, q)
            } catch (e: Exception) {
                history.remove(history.length() - 1)
                setStatus("ERROR")
                speak("Ha fallado la consulta. Revisa la conexión y la clave de la API.")
            }
        }
    }

    private fun callClaude(): String {
        val body = JSONObject()
            .put("model", model)
            .put("max_tokens", 300)
            .put("system", system)
            .put("messages", history)
        val conn = (URL("https://api.anthropic.com/v1/messages").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 10000
            readTimeout = 30000
            doOutput = true
            setRequestProperty("content-type", "application/json")
            setRequestProperty("x-api-key", apiKey())
            setRequestProperty("anthropic-version", "2023-06-01")
        }
        conn.outputStream.use { it.write(body.toString().toByteArray()) }
        val stream = if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream
        val resp = stream.bufferedReader().use { it.readText() }
        if (conn.responseCode !in 200..299) throw RuntimeException(resp)
        return JSONObject(resp).getJSONArray("content").getJSONObject(0).getString("text")
    }

    // ---------- Abrir apps ----------

    private fun openApp(app: String, q: String) {
        val e = URLEncoder.encode(q, "UTF-8")
        val (uri, pkg) = when (app) {
            "navegar" -> Uri.parse("waze://?q=$e&navigate=yes") to "com.waze"
            "musica" -> Uri.parse("spotify:search:$e") to "com.spotify.music"
            "youtube" -> Uri.parse("https://music.youtube.com/search?q=$e") to "com.google.android.apps.youtube.music"
            "mensaje" -> Uri.parse("https://wa.me/?text=$e") to "com.whatsapp"
            "llamar" -> Uri.parse("tel:" + q.filter { it.isDigit() || it == '+' }) to null
            else -> return
        }
        val intent = Intent(if (app == "llamar") Intent.ACTION_DIAL else Intent.ACTION_VIEW, uri)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            if (pkg != null) intent.setPackage(pkg)
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            try {
                // Si la app concreta no está instalada, deja que Android elija otra.
                intent.setPackage(null)
                if (app == "navegar") intent.data = Uri.parse("google.navigation:q=$e")
                startActivity(intent)
            } catch (_: ActivityNotFoundException) {
                speak("No encuentro ninguna app para eso en este móvil.")
            }
        }
    }

    override fun onDestroy() {
        recognizer?.destroy()
        tts.shutdown()
        super.onDestroy()
    }
}
