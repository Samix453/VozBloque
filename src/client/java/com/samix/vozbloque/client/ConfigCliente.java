package com.samix.vozbloque.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Ajustes de cada jugador: config/vozbloque-cliente.json */
public class ConfigCliente {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

	/** Parte del nombre del micrófono a usar (vacío = el predeterminado de Windows). Ver /voz micros */
	public String microfono = "";
	/** Reaccionar mientras aún estás hablando (más rápido) en vez de esperar a que termines la frase. */
	public boolean reaccionRapida = true;
	/**
	 * Cómo escucha el reconocedor:
	 *  "mixto"   = palabras de bloques + palabras comunes de conversación (recomendado)
	 *  "bloques" = SOLO palabras de bloques (detecta muy bien los bloques, pero confunde más cosas con bloques)
	 *  "libre"   = español completo (entiende todo, pero falla más con los nombres de bloques)
	 */
	public String modo = "mixto";
	/** "negro" también borra cosas "negras", "rojo" las "rojas", etc. */
	public boolean coincidirGenero = true;
	/** Palabras que nunca borran nada (artículos, preposiciones...). */
	public List<String> palabrasIgnoradas = new ArrayList<>(List.of(
			"de", "del", "la", "las", "el", "los", "lo", "y", "e", "o", "u", "con", "en", "a", "al",
			"para", "por", "un", "una", "unos", "unas", "sin", "sobre", "bloque", "bloques"
	));
	/** No repetir la misma palabra antes de este tiempo (ms). */
	public int esperaRepeticionMs = 1500;
	/** Mostrar el indicador de micrófono y el texto reconocido arriba a la izquierda. */
	public boolean mostrarHud = true;
	/** Empezar con el micro silenciado. */
	public boolean empezarSilenciado = false;
	/** Modelo de voz (español, pequeño, ~40 MB). */
	public String urlModelo = "https://alphacephei.com/vosk/models/vosk-model-small-es-0.42.zip";

	public static Path ruta() {
		return FabricLoader.getInstance().getConfigDir().resolve("vozbloque-cliente.json");
	}

	public static ConfigCliente cargar() {
		Path p = ruta();
		ConfigCliente c = null;
		if (Files.exists(p)) {
			try (Reader r = Files.newBufferedReader(p, StandardCharsets.UTF_8)) {
				c = GSON.fromJson(r, ConfigCliente.class);
			} catch (Exception e) {
				VozBloqueClient.LOG.error("No se pudo leer {}, se usan valores por defecto", p, e);
			}
		}
		if (c == null) c = new ConfigCliente();
		if (c.palabrasIgnoradas == null) c.palabrasIgnoradas = new ArrayList<>();
		if (c.microfono == null) c.microfono = "";
		if (c.modo == null || !List.of("mixto", "bloques", "libre").contains(c.modo)) c.modo = "mixto";
		if (c.urlModelo == null || c.urlModelo.isBlank()) c.urlModelo = new ConfigCliente().urlModelo;
		c.guardar();
		return c;
	}

	public void guardar() {
		try {
			Files.createDirectories(ruta().getParent());
			try (Writer w = Files.newBufferedWriter(ruta(), StandardCharsets.UTF_8)) {
				GSON.toJson(this, w);
			}
		} catch (Exception e) {
			VozBloqueClient.LOG.error("No se pudo guardar la config del cliente", e);
		}
	}
}
