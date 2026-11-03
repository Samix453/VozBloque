package com.samix.vozbloque.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.level.block.Block;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Índice "palabra → bloques" construido con los nombres de los bloques EN ESPAÑOL tal y como salen
 * en el juego (lang/es_es.json), aunque el jugador tenga el juego en otro idioma.
 */
public final class IndiceBloques {
	/** raíz de palabra → ids de bloque */
	private final Map<String, Set<ResourceLocation>> porRaiz = new HashMap<>();
	/** id → nombre en español */
	private final Map<ResourceLocation, String> nombres = new LinkedHashMap<>();
	/** palabras tal cual (minúsculas, con tildes) para el modo gramática */
	private final Set<String> vocabulario = new TreeSet<>();
	private final Set<String> ignoradas = new HashSet<>();
	private final boolean genero;
	private final boolean espanolEncontrado;

	public IndiceBloques(ResourceManager rm, ConfigCliente cfg) {
		this.genero = cfg.coincidirGenero;
		for (String w : cfg.palabrasIgnoradas) {
			String n = Texto.normalizar(w.trim());
			if (n.isEmpty()) continue;
			ignoradas.add(n);
			ignoradas.add(Texto.raiz(n, genero));
		}

		Map<String, String> es = cargarIdioma(rm, "es_es");
		espanolEncontrado = !es.isEmpty();

		for (Block b : BuiltInRegistries.BLOCK) {
			if (b.defaultBlockState().isAir()) continue;
			ResourceLocation id = BuiltInRegistries.BLOCK.getKey(b);
			String clave = b.getDescriptionId();
			String nombre = es.get(clave);
			if (nombre == null) nombre = I18n.get(clave);
			if (nombre == null || nombre.isBlank() || nombre.equals(clave)) continue;
			nombres.put(id, nombre);

			for (String crudo : nombre.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+")) {
				if (crudo.isEmpty()) continue;
				String n = Texto.normalizar(crudo);
				if (n.length() < 3 || ignoradas.contains(n)) continue;
				String r = Texto.raiz(n, genero);
				if (ignoradas.contains(r)) continue;
				porRaiz.computeIfAbsent(r, k -> new LinkedHashSet<>()).add(id);
				vocabulario.add(crudo);
			}
		}
	}

	private static Map<String, String> cargarIdioma(ResourceManager rm, String codigo) {
		Map<String, String> out = new HashMap<>();
		for (String ns : rm.getNamespaces()) {
			ResourceLocation loc;
			try {
				loc = new ResourceLocation(ns, "lang/" + codigo + ".json");
			} catch (Exception e) {
				continue;
			}
			try {
				for (Resource res : rm.getResourceStack(loc)) {
					try (Reader r = new InputStreamReader(res.open(), StandardCharsets.UTF_8)) {
						JsonElement el = JsonParser.parseReader(r);
						if (!el.isJsonObject()) continue;
						JsonObject obj = el.getAsJsonObject();
						for (Map.Entry<String, JsonElement> e : obj.entrySet()) {
							if (e.getValue().isJsonPrimitive()) out.put(e.getKey(), e.getValue().getAsString());
						}
					}
				}
			} catch (Exception e) {
				VozBloqueClient.LOG.warn("No se pudo leer {}: {}", loc, e.toString());
			}
		}
		return out;
	}

	public boolean espanolEncontrado() {
		return espanolEncontrado;
	}

	/** Raíz utilizable de una palabra dicha, o null si se ignora. */
	public String raizDe(String palabraDicha) {
		String n = Texto.normalizar(palabraDicha);
		if (n.length() < 3 || ignoradas.contains(n)) return null;
		String r = Texto.raiz(n, genero);
		if (ignoradas.contains(r)) return null;
		return r;
	}

	public Set<ResourceLocation> buscar(String palabraDicha) {
		String r = raizDe(palabraDicha);
		if (r == null) return Collections.emptySet();
		return porRaiz.getOrDefault(r, Collections.emptySet());
	}

	public String nombre(ResourceLocation id) {
		return nombres.getOrDefault(id, id.toString());
	}

	public int numBloques() {
		return nombres.size();
	}

	/** Gramática JSON para Vosk según el modo, o null para el modo libre. */
	public String gramaticaJson(String modo) {
		if ("libre".equals(modo)) return null;
		return PalabrasComunes.gramatica(vocabulario, "mixto".equals(modo));
	}
}
