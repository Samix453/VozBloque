import com.samix.vozbloque.client.ConfigCliente;
import com.samix.vozbloque.client.MotorVoz;
import com.samix.vozbloque.client.Texto;
import com.samix.vozbloque.client.VoskLib;
import com.sun.jna.Pointer;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

/**
 * Prueba automática (GitHub Actions): descarga el motor nativo y el modelo con el MISMO código del mod,
 * y reconoce un audio en español generado por síntesis de voz.
 * Uso: java PruebaVosk <carpeta> <audio.wav> <palabra-esperada>
 */
public class PruebaVosk {
	public static void main(String[] args) throws Exception {
		Path carpeta = Path.of(args[0]);
		File wav = new File(args[1]);
		String esperada = args[2];

		MotorVoz motor = new MotorVoz(new ConfigCliente(), carpeta);
		VoskLib vosk = motor.cargarNativos();
		System.out.println("Nativos cargados OK");
		Path modelo = motor.asegurarModelo();
		System.out.println("Modelo en " + modelo);
		vosk.vosk_set_log_level(-1);
		Pointer model = vosk.vosk_model_new(modelo.toAbsolutePath().toString());
		if (model == null) throw new IllegalStateException("vosk_model_new devolvió null");

		String libre = reconocer(vosk, model, wav, null);
		System.out.println("Modo libre: «" + libre + "»");
		String gram = reconocer(vosk, model, wav, "[\"piedra\", \"tierra\", \"roble\", \"césped\", \"arena\", \"[unk]\"]");
		System.out.println("Modo gramática: «" + gram + "»");

		boolean ok = false;
		for (String w : Texto.palabras(libre)) if (Texto.raiz(w, true).equals(Texto.raiz(Texto.normalizar(esperada), true))) ok = true;
		if (!ok) {
			System.out.println("FALLO: no se reconoció «" + esperada + "»");
			System.exit(1);
		}
		System.out.println("PRUEBA SUPERADA");
	}

	private static String reconocer(VoskLib vosk, Pointer model, File wav, String gramatica) throws Exception {
		try (AudioInputStream in = AudioSystem.getAudioInputStream(wav)) {
			AudioFormat f = in.getFormat();
			System.out.println("Audio: " + f);
			Pointer rec = gramatica == null
					? vosk.vosk_recognizer_new(model, f.getSampleRate())
					: vosk.vosk_recognizer_new_grm(model, f.getSampleRate(), (gramatica + "\0").getBytes(StandardCharsets.UTF_8));
			StringBuilder sb = new StringBuilder();
			byte[] buf = new byte[3200];
			int n;
			while ((n = in.read(buf)) > 0) {
				if (vosk.vosk_recognizer_accept_waveform(rec, buf, n) == 1) {
					sb.append(campo(vosk.vosk_recognizer_result(rec))).append(' ');
				} else {
					String p = vosk.vosk_recognizer_partial_result(rec).getString(0, "UTF-8");
					if (!p.contains("\"\"")) System.out.println("  parcial " + p.replaceAll("\\s+", " "));
				}
			}
			sb.append(campo(vosk.vosk_recognizer_final_result(rec)));
			vosk.vosk_recognizer_free(rec);
			return sb.toString().trim();
		}
	}

	private static String campo(Pointer p) {
		String json = p.getString(0, "UTF-8");
		return com.google.gson.JsonParser.parseString(json).getAsJsonObject().get("text").getAsString();
	}
}
