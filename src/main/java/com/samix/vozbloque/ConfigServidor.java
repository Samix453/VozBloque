package com.samix.vozbloque;

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

/** Ajustes del servidor: config/vozbloque-servidor.json */
public class ConfigServidor {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	/** Si es false, nadie borra nada (útil para pausar el reto). */
	public boolean activo = true;
	/** Radio horizontal (en bloques) alrededor del jugador. */
	public int radioHorizontal = 32;
	/** Radio vertical. -1 = toda la altura del mundo. */
	public int radioVertical = 32;
	/** "jugador" = solo alrededor de quien lo dice. "todos" = alrededor de todos los jugadores. */
	public String zona = "jugador";
	/** Cuántos bloques se borran como máximo por tick (para no congelar el servidor). */
	public int bloquesPorTick = 20000;
	/** Reproducir un sonido cuando se borran bloques. */
	public boolean sonido = true;
	/** Bloques que nunca se borran. */
	public List<String> bloquesProtegidos = new ArrayList<>(List.of(
			"minecraft:bedrock",
			"minecraft:end_portal_frame",
			"minecraft:end_portal"
	));

	public static Path ruta() {
		return FabricLoader.getInstance().getConfigDir().resolve("vozbloque-servidor.json");
	}

	public static ConfigServidor cargar() {
		Path p = ruta();
		ConfigServidor c = null;
		if (Files.exists(p)) {
			try (Reader r = Files.newBufferedReader(p, StandardCharsets.UTF_8)) {
				c = GSON.fromJson(r, ConfigServidor.class);
			} catch (Exception e) {
				VozBloque.LOG.error("No se pudo leer {}, se usan valores por defecto", p, e);
			}
		}
		if (c == null) c = new ConfigServidor();
		if (c.bloquesProtegidos == null) c.bloquesProtegidos = new ArrayList<>();
		if (c.zona == null) c.zona = "jugador";
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
			VozBloque.LOG.error("No se pudo guardar la config del servidor", e);
		}
	}
}
