package com.samix.vozbloque.client;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** Normalización de palabras para comparar lo que se dice con los nombres de los bloques. */
public final class Texto {
	private static final Pattern MARCAS = Pattern.compile("\\p{M}+");
	private static final Pattern SEPARADOR = Pattern.compile("[^a-z0-9]+");

	private Texto() {}

	/** minúsculas y sin tildes: "Césped" → "cesped", "Piña" → "pina". */
	public static String normalizar(String s) {
		String n = Normalizer.normalize(s.toLowerCase(Locale.ROOT), Normalizer.Form.NFD);
		return MARCAS.matcher(n).replaceAll("");
	}

	/** Separa en palabras normalizadas. */
	public static List<String> palabras(String s) {
		List<String> out = new ArrayList<>();
		for (String p : SEPARADOR.split(normalizar(s))) if (!p.isEmpty()) out.add(p);
		return out;
	}

	private static boolean vocal(char c) {
		return c == 'a' || c == 'e' || c == 'i' || c == 'o' || c == 'u';
	}

	/**
	 * Raíz de una palabra ya normalizada, para que singular/plural (y opcionalmente masculino/femenino)
	 * cuenten como la misma palabra: "piedras" = "piedra", "flores" = "flor", "luces" = "luz",
	 * "negro" = "negra".
	 */
	public static String raiz(String w, boolean genero) {
		int n = w.length();
		if (n > 4 && w.endsWith("ces")) {
			w = w.substring(0, n - 3) + "z";
		} else if (n > 4 && w.endsWith("es") && !vocal(w.charAt(n - 3))) {
			w = w.substring(0, n - 2);
		} else if (n > 3 && w.endsWith("s")) {
			w = w.substring(0, n - 1);
		}
		if (genero && w.length() >= 4 && (w.endsWith("a") || w.endsWith("o"))) {
			w = w.substring(0, w.length() - 1);
		}
		return w;
	}
}
