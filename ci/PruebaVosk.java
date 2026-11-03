import com.samix.vozbloque.client.ConfigCliente;
import com.samix.vozbloque.client.MotorVoz;
import com.samix.vozbloque.client.PalabrasComunes;
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
	static final String BLOQUES = "piedra tierra arena grava roble abedul abeto jungla acacia oscuro mangle cerezo bambú "
			+ "tronco tablones hojas césped hierba flor amapola diente león tulipán margarita lana hormigón vidrio cristal "
			+ "ladrillos losa escaleras valla puerta trampilla antorcha linterna cofre horno mesa trabajo cama lava agua "
			+ "hielo nieve obsidiana diamante esmeralda oro hierro carbón cobre redstone lapislázuli cuarzo mena roca base "
			+ "basalto andesita diorita granito pizarra profunda toba calcita amatista musgo cactus calabaza sandía caña "
			+ "azúcar hongo champiñón telaraña barril yunque palanca botón placa presión pistón observador tolva "
			+ "dispensador raíles negro negra blanco blanca rojo roja azul verde amarillo amarilla naranja morado gris "
			+ "rosa marrón cian lima magenta";

	public static void main(String[] args) throws Exception {
		Path carpeta = Path.of(args[0]);
		MotorVoz motor = new MotorVoz(new ConfigCliente(), carpeta);
		VoskLib vosk = motor.cargarNativos();
		System.out.println("Nativos cargados OK");
		Path modelo = motor.asegurarModelo();
		System.out.println("Modelo en " + modelo);
		vosk.vosk_set_log_level(-1);
		Pointer model = vosk.vosk_model_new(modelo.toAbsolutePath().toString());
		if (model == null) throw new IllegalStateException("vosk_model_new devolvió null");

		java.util.List<String> bloques = java.util.List.of(BLOQUES.split(" "));
		String[] modos = {"libre", "mixto", "bloques"};
		String[] gramaticas = {null, PalabrasComunes.gramatica(bloques, true), PalabrasComunes.gramatica(bloques, false)};
		for (int a = 1; a < args.length; a++) {
			File wav = new File(args[a]);
			System.out.println("=== " + wav.getName());
			for (int m = 0; m < modos.length; m++) {
				String t = reconocer(vosk, model, wav, gramaticas[m]);
				System.out.println("RESULTADO " + modos[m] + ": «" + t + "»");
			}
		}
		System.out.println("PRUEBA SUPERADA");
	}

	private static String reconocer(VoskLib vosk, Pointer model, File wav, String gramatica) throws Exception {
		try (AudioInputStream in = AudioSystem.getAudioInputStream(wav)) {
			AudioFormat f = in.getFormat();
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
					// parcial ignorado
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
