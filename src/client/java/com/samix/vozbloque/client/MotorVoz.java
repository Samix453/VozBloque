package com.samix.vozbloque.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.TargetDataLine;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Hilo que escucha el micrófono y reconoce voz en español con Vosk (sin internet, todo en tu PC).
 * La primera vez descarga el modelo de voz (~40 MB) y la librería nativa de Vosk.
 */
public final class MotorVoz implements Runnable {
	private static final Logger LOG = LoggerFactory.getLogger("VozBloque/Voz");

	public enum Estado { INICIANDO, DESCARGANDO, CARGANDO, ESCUCHANDO, ERROR }

	public record Reconocido(String texto, boolean parcial) {}

	private static final String VOSK_VERSION = "0.3.45";
	private static final String VOSK_VERSION_MAC = "0.3.44";
	private static final float[] FRECUENCIAS = {16000f, 48000f, 44100f};

	public final ConcurrentLinkedQueue<Reconocido> resultados = new ConcurrentLinkedQueue<>();

	public volatile Estado estado = Estado.INICIANDO;
	public volatile String detalle = "Preparando reconocimiento de voz…";
	public volatile int progreso = -1;
	public volatile float nivel = 0f;
	public volatile boolean silenciado;
	public volatile String microUsado = "";

	private volatile String gramatica = null;
	private volatile boolean reiniciarMicro = false;
	private final ConfigCliente cfg;
	private final Path carpeta;

	public MotorVoz(ConfigCliente cfg) {
		this(cfg, FabricLoader.getInstance().getGameDir().resolve("vozbloque"));
	}

	/** Constructor sin Fabric (para las pruebas automáticas). */
	public MotorVoz(ConfigCliente cfg, Path carpeta) {
		this.cfg = cfg;
		this.silenciado = cfg.empezarSilenciado;
		this.carpeta = carpeta;
	}

	public void ponerGramatica(String json) {
		this.gramatica = json;
	}

	public void reiniciarMicro() {
		this.reiniciarMicro = true;
	}

	@Override
	public void run() {
		try {
			VoskLib vosk = cargarNativos();
			Path modelo = asegurarModelo();

			estado = Estado.CARGANDO;
			detalle = "Cargando modelo de voz…";
			progreso = -1;
			vosk.vosk_set_log_level(-1);
			Pointer model = vosk.vosk_model_new(modelo.toAbsolutePath().toString());
			if (model == null) throw new IOException("Vosk no pudo cargar el modelo en " + modelo);

			while (true) {
				bucleMicro(vosk, model);
				Thread.sleep(500);
			}
		} catch (Throwable t) {
			estado = Estado.ERROR;
			detalle = "Error de voz: " + t.getMessage();
			LOG.error("El reconocimiento de voz ha fallado", t);
		}
	}

	// ---------------------------------------------------------------- micrófono

	private void bucleMicro(VoskLib vosk, Pointer model) throws Exception {
		reiniciarMicro = false;
		TargetDataLine linea = null;
		float frecuencia = 0;
		for (float f : FRECUENCIAS) {
			try {
				linea = abrirMicro(f);
				if (linea != null) {
					frecuencia = f;
					break;
				}
			} catch (Exception e) {
				LOG.debug("No se pudo abrir el micro a {} Hz: {}", f, e.toString());
			}
		}
		if (linea == null) {
			estado = Estado.ERROR;
			detalle = "No se encuentra el micrófono" + (cfg.microfono.isBlank() ? "" : " «" + cfg.microfono + "»") + ". Usa /voz micros";
			while (!reiniciarMicro) Thread.sleep(500);
			return;
		}

		LOG.info("Micrófono abierto: {} a {} Hz", microUsado, frecuencia);
		String gramaticaActual = null;
		Pointer rec = null;
		byte[] buf = new byte[(int) (frecuencia / 10) * 2]; // ~100 ms
		boolean estabaSilenciado = false;
		String ultimoParcial = "";
		try {
			linea.start();
			while (!reiniciarMicro) {
				String g = cfg.modoGramatica ? gramatica : null;
				if (rec == null || !java.util.Objects.equals(g, gramaticaActual)) {
					if (rec != null) vosk.vosk_recognizer_free(rec);
					if (g != null) {
						byte[] utf = (g + "\0").getBytes(StandardCharsets.UTF_8);
						rec = vosk.vosk_recognizer_new_grm(model, frecuencia, utf);
					} else {
						rec = vosk.vosk_recognizer_new(model, frecuencia);
					}
					if (rec == null) throw new IOException("Vosk no pudo crear el reconocedor");
					gramaticaActual = g;
				}

				estado = Estado.ESCUCHANDO;
				detalle = "";
				int n = linea.read(buf, 0, buf.length);
				if (n <= 0) continue;
				nivel = calcularNivel(buf, n);

				if (silenciado) {
					if (!estabaSilenciado) {
						vosk.vosk_recognizer_reset(rec);
						ultimoParcial = "";
						estabaSilenciado = true;
					}
					continue;
				}
				estabaSilenciado = false;

				int r = vosk.vosk_recognizer_accept_waveform(rec, buf, n);
				if (r == 1) {
					String texto = leerCampo(vosk.vosk_recognizer_result(rec), "text");
					ultimoParcial = "";
					if (!texto.isBlank()) resultados.add(new Reconocido(texto, false));
				} else if (r == 0) {
					String parcial = leerCampo(vosk.vosk_recognizer_partial_result(rec), "partial");
					if (!parcial.isBlank() && !parcial.equals(ultimoParcial)) {
						ultimoParcial = parcial;
						resultados.add(new Reconocido(parcial, true));
					}
				}
			}
		} finally {
			if (rec != null) vosk.vosk_recognizer_free(rec);
			linea.stop();
			linea.close();
		}
	}

	private TargetDataLine abrirMicro(float frecuencia) throws Exception {
		AudioFormat formato = new AudioFormat(frecuencia, 16, 1, true, false);
		DataLine.Info info = new DataLine.Info(TargetDataLine.class, formato);
		TargetDataLine linea = null;
		if (!cfg.microfono.isBlank()) {
			String buscado = cfg.microfono.toLowerCase(Locale.ROOT);
			for (Mixer.Info mi : AudioSystem.getMixerInfo()) {
				if (!mi.getName().toLowerCase(Locale.ROOT).contains(buscado)) continue;
				Mixer m = AudioSystem.getMixer(mi);
				if (m.isLineSupported(info)) {
					linea = (TargetDataLine) m.getLine(info);
					microUsado = mi.getName();
					break;
				}
			}
			if (linea == null) return null;
		} else {
			if (!AudioSystem.isLineSupported(info)) return null;
			linea = (TargetDataLine) AudioSystem.getLine(info);
			microUsado = "predeterminado";
		}
		linea.open(formato, buf(frecuencia));
		return linea;
	}

	private static int buf(float f) {
		return (int) (f / 5) * 2; // 200 ms de búfer
	}

	public static List<String> listarMicros() {
		List<String> out = new ArrayList<>();
		for (Mixer.Info mi : AudioSystem.getMixerInfo()) {
			try {
				Mixer m = AudioSystem.getMixer(mi);
				for (javax.sound.sampled.Line.Info li : m.getTargetLineInfo()) {
					if (TargetDataLine.class.isAssignableFrom(li.getLineClass())) {
						out.add(mi.getName());
						break;
					}
				}
			} catch (Exception ignored) {
			}
		}
		return out;
	}

	private static float calcularNivel(byte[] b, int n) {
		int max = 0;
		for (int i = 0; i + 1 < n; i += 2) {
			int s = (short) ((b[i] & 0xff) | (b[i + 1] << 8));
			if (s < 0) s = -s;
			if (s > max) max = s;
		}
		return Math.min(1f, max / 20000f);
	}

	private static String leerCampo(Pointer p, String campo) {
		if (p == null) return "";
		String json = p.getString(0, "UTF-8");
		try {
			JsonObject o = JsonParser.parseString(json).getAsJsonObject();
			JsonElement e = o.get(campo);
			return e == null ? "" : e.getAsString().trim();
		} catch (Exception e) {
			return "";
		}
	}

	// ---------------------------------------------------------------- descargas

	public Path asegurarModelo() throws Exception {
		Files.createDirectories(carpeta);
		String url = cfg.urlModelo;
		String archivo = url.substring(url.lastIndexOf('/') + 1);
		String nombre = archivo.endsWith(".zip") ? archivo.substring(0, archivo.length() - 4) : archivo;
		Path dir = carpeta.resolve(nombre);
		Path hecho = dir.resolve(".completo");
		if (Files.exists(hecho)) return dir;

		estado = Estado.DESCARGANDO;
		detalle = "Descargando modelo de voz en español";
		Path zip = carpeta.resolve(archivo);
		descargar(url, zip);

		detalle = "Descomprimiendo modelo de voz…";
		progreso = -1;
		Path raiz = descomprimir(zip, carpeta, null);
		Files.deleteIfExists(zip);
		Path real = Files.isDirectory(dir) ? dir : raiz;
		if (real == null || !Files.isDirectory(real)) throw new IOException("El zip del modelo no tiene el formato esperado");
		Files.writeString(real.resolve(".completo"), "ok");
		return real;
	}

	public VoskLib cargarNativos() throws Exception {
		Files.createDirectories(carpeta);
		String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
		String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
		boolean arm = arch.contains("aarch64") || arch.contains("arm64");
		String version = VOSK_VERSION;
		String etiqueta;
		String principal;
		String[] dependencias = new String[0];
		if (os.contains("win")) {
			etiqueta = "win_amd64";
			principal = "libvosk.dll";
			dependencias = new String[]{"libwinpthread-1.dll", "libgcc_s_seh-1.dll", "libstdc++-6.dll"};
		} else if (os.contains("mac") || os.contains("darwin")) {
			version = VOSK_VERSION_MAC;
			etiqueta = "macosx";
			principal = "libvosk.dyld";
		} else {
			etiqueta = arm ? "aarch64" : "x86_64";
			principal = "libvosk.so";
		}

		Path dir = carpeta.resolve("nativos-" + version + "-" + etiqueta);
		Path hecho = dir.resolve(".completo");
		if (!Files.exists(hecho)) {
			estado = Estado.DESCARGANDO;
			detalle = "Descargando motor de voz (Vosk)";
			String urlWheel = buscarWheel(version, etiqueta);
			Path wheel = carpeta.resolve("vosk-" + etiqueta + ".whl");
			descargar(urlWheel, wheel);
			Files.createDirectories(dir);
			descomprimir(wheel, dir, n -> n.startsWith("vosk/") && (n.endsWith(".dll") || n.endsWith(".so")
					|| n.endsWith(".dylib") || n.endsWith(".dyld")));
			Files.deleteIfExists(wheel);
			Files.writeString(hecho, "ok");
		}

		estado = Estado.CARGANDO;
		detalle = "Cargando motor de voz…";
		for (String d : dependencias) {
			Path p = dir.resolve(d);
			if (Files.exists(p)) System.load(p.toAbsolutePath().toString());
		}
		Path lib = dir.resolve(principal);
		if (!Files.exists(lib)) throw new IOException("Falta " + lib);
		return Native.load(lib.toAbsolutePath().toString(), VoskLib.class);
	}

	/** Busca en PyPI el paquete de Vosk para este sistema (contiene la librería nativa). */
	private static String buscarWheel(String version, String etiqueta) throws Exception {
		String json = new String(get("https://pypi.org/pypi/vosk/" + version + "/json").readAllBytes(), StandardCharsets.UTF_8);
		JsonArray urls = JsonParser.parseString(json).getAsJsonObject().getAsJsonArray("urls");
		for (JsonElement e : urls) {
			JsonObject o = e.getAsJsonObject();
			String fn = o.get("filename").getAsString();
			if (fn.endsWith(".whl") && fn.contains(etiqueta)) return o.get("url").getAsString();
		}
		throw new IOException("No hay Vosk " + version + " para " + etiqueta);
	}

	private static HttpClient http() {
		return HttpClient.newBuilder()
				.followRedirects(HttpClient.Redirect.NORMAL)
				.connectTimeout(Duration.ofSeconds(20))
				.build();
	}

	private static InputStream get(String url) throws Exception {
		HttpRequest req = HttpRequest.newBuilder(URI.create(url)).header("User-Agent", "VozBloque-MinecraftMod").GET().build();
		HttpResponse<InputStream> resp = http().send(req, HttpResponse.BodyHandlers.ofInputStream());
		if (resp.statusCode() != 200) throw new IOException("HTTP " + resp.statusCode() + " en " + url);
		return resp.body();
	}

	private void descargar(String url, Path destino) throws Exception {
		LOG.info("Descargando {}", url);
		HttpRequest req = HttpRequest.newBuilder(URI.create(url)).header("User-Agent", "VozBloque-MinecraftMod").GET().build();
		HttpResponse<InputStream> resp = http().send(req, HttpResponse.BodyHandlers.ofInputStream());
		if (resp.statusCode() != 200) throw new IOException("HTTP " + resp.statusCode() + " al descargar " + url);
		long total = resp.headers().firstValueAsLong("Content-Length").orElse(-1L);
		Path tmp = destino.resolveSibling(destino.getFileName() + ".part");
		try (InputStream in = resp.body(); OutputStream out = Files.newOutputStream(tmp)) {
			byte[] b = new byte[64 * 1024];
			long leido = 0;
			int n;
			while ((n = in.read(b)) > 0) {
				out.write(b, 0, n);
				leido += n;
				progreso = total > 0 ? (int) (leido * 100 / total) : -1;
			}
		}
		Files.move(tmp, destino, StandardCopyOption.REPLACE_EXISTING);
		progreso = -1;
	}

	/**
	 * Descomprime. Si filtro != null, solo extrae esos archivos y los deja planos en destino.
	 * Devuelve la primera carpeta de nivel superior encontrada (para el modelo).
	 */
	private static Path descomprimir(Path zip, Path destino, java.util.function.Predicate<String> filtro) throws IOException {
		Path primera = null;
		Path base = destino.toAbsolutePath().normalize();
		try (ZipInputStream zin = new ZipInputStream(Files.newInputStream(zip))) {
			ZipEntry e;
			while ((e = zin.getNextEntry()) != null) {
				String nombre = e.getName().replace('\\', '/');
				Path salida;
				if (filtro != null) {
					if (e.isDirectory() || !filtro.test(nombre)) continue;
					salida = base.resolve(nombre.substring(nombre.lastIndexOf('/') + 1)).normalize();
				} else {
					salida = base.resolve(nombre).normalize();
					if (primera == null && nombre.contains("/")) {
						primera = base.resolve(nombre.substring(0, nombre.indexOf('/'))).normalize();
					}
				}
				if (!salida.startsWith(base)) throw new IOException("Entrada sospechosa en el zip: " + nombre);
				if (e.isDirectory()) {
					Files.createDirectories(salida);
				} else {
					Files.createDirectories(salida.getParent());
					Files.copy(zin, salida, StandardCopyOption.REPLACE_EXISTING);
				}
			}
		}
		return primera;
	}
}
