package com.samix.vozbloque.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.samix.vozbloque.Red;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class VozBloqueClient implements ClientModInitializer {
	public static final Logger LOG = LoggerFactory.getLogger("VozBloque/Cliente");

	private static ConfigCliente config;
	private static MotorVoz motor;
	private static IndiceBloques indice;
	private static KeyMapping teclaSilenciar;

	private static int disparadasEnFrase = 0;
	private static final Map<String, Long> ultimaVez = new HashMap<>();
	private static final List<Red.Dicho> pendientes = new ArrayList<>();
	private static String textoHud = "";
	private static long textoHudHasta = 0;
	private static boolean avisadoSinServidor = false;

	@Override
	public void onInitializeClient() {
		config = ConfigCliente.cargar();
		motor = new MotorVoz(config);
		Thread hilo = new Thread(motor, "VozBloque-Voz");
		hilo.setDaemon(true);
		hilo.start();

		teclaSilenciar = KeyBindingHelper.registerKeyBinding(new KeyMapping(
				"key.vozbloque.silenciar", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_J, "category.vozbloque"));

		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
			client.execute(() -> {
				reconstruirIndice(client);
				avisadoSinServidor = false;
			});
		});

		ClientTickEvents.END_CLIENT_TICK.register(VozBloqueClient::tick);
		HudRenderCallback.EVENT.register(VozBloqueClient::dibujarHud);
		registrarComandos();
	}

	private static void reconstruirIndice(Minecraft mc) {
		try {
			indice = new IndiceBloques(mc.getResourceManager(), config);
			motor.ponerGramatica(indice.gramaticaJson());
			LOG.info("Índice de bloques listo: {} bloques (español {})", indice.numBloques(),
					indice.espanolEncontrado() ? "OK" : "NO encontrado, se usa el idioma del juego");
		} catch (Exception e) {
			LOG.error("No se pudo crear el índice de bloques", e);
		}
	}

	private static IndiceBloques indice() {
		if (indice == null) reconstruirIndice(Minecraft.getInstance());
		return indice;
	}

	// ------------------------------------------------------------------ tick

	private static void tick(Minecraft mc) {
		while (teclaSilenciar.consumeClick()) alternarSilencio(mc);

		MotorVoz.Reconocido r;
		while ((r = motor.resultados.poll()) != null) {
			textoHud = r.texto();
			textoHudHasta = System.currentTimeMillis() + 4000;
			if (mc.player == null) {
				disparadasEnFrase = 0;
				continue;
			}
			procesar(r.texto(), r.parcial());
		}

		if (!pendientes.isEmpty()) {
			enviar(mc, new ArrayList<>(pendientes));
			pendientes.clear();
		}
	}

	private static void procesar(String texto, boolean parcial) {
		String[] palabras = texto.trim().split("\\s+");
		if (parcial) {
			if (!config.reaccionRapida) return;
			// La última palabra todavía puede cambiar mientras hablas: solo usamos las anteriores.
			int hasta = palabras.length - 1;
			for (int i = disparadasEnFrase; i < hasta; i++) disparar(palabras[i]);
			disparadasEnFrase = Math.max(disparadasEnFrase, hasta);
		} else {
			for (int i = disparadasEnFrase; i < palabras.length; i++) disparar(palabras[i]);
			disparadasEnFrase = 0;
		}
	}

	private static void disparar(String palabra) {
		if (palabra.isEmpty() || palabra.startsWith("[")) return;
		IndiceBloques idx = indice();
		if (idx == null) return;
		Set<ResourceLocation> ids = idx.buscar(palabra);
		if (ids.isEmpty()) return;
		String raiz = idx.raizDe(palabra);
		long ahora = System.currentTimeMillis();
		Long antes = ultimaVez.get(raiz);
		if (antes != null && ahora - antes < config.esperaRepeticionMs) return;
		ultimaVez.put(raiz, ahora);
		pendientes.add(new Red.Dicho(palabra, new ArrayList<>(ids)));
	}

	private static void enviar(Minecraft mc, List<Red.Dicho> dichos) {
		if (mc.player == null || mc.getConnection() == null) return;
		if (!ClientPlayNetworking.canSend(Red.DICHO)) {
			if (!avisadoSinServidor) {
				avisadoSinServidor = true;
				mc.player.displayClientMessage(Component.literal(
						"[VozBloque] Este servidor no tiene el mod instalado: no se puede borrar nada.")
						.withStyle(ChatFormatting.RED), false);
			}
			return;
		}
		FriendlyByteBuf buf = PacketByteBufs.create();
		Red.escribir(buf, dichos);
		ClientPlayNetworking.send(Red.DICHO, buf);
	}

	private static void alternarSilencio(Minecraft mc) {
		motor.silenciado = !motor.silenciado;
		if (mc.player != null) {
			mc.player.displayClientMessage(Component.literal(motor.silenciado ? "Micrófono SILENCIADO" : "Micrófono ACTIVADO")
					.withStyle(motor.silenciado ? ChatFormatting.RED : ChatFormatting.GREEN), true);
		}
	}

	// ------------------------------------------------------------------ HUD

	private static void dibujarHud(GuiGraphics g, float delta) {
		Minecraft mc = Minecraft.getInstance();
		if (!config.mostrarHud || mc.options.hideGui || mc.player == null) return;
		int x = 4, y = 4;
		String linea;
		int color;
		switch (motor.estado) {
			case ESCUCHANDO -> {
				if (motor.silenciado) {
					linea = "● Micro silenciado (" + teclaSilenciar.getTranslatedKeyMessage().getString() + ")";
					color = 0xFF5555;
				} else {
					linea = "● Escuchando";
					color = 0x55FF55;
				}
			}
			case DESCARGANDO -> {
				linea = motor.detalle + (motor.progreso >= 0 ? " " + motor.progreso + "%" : "…");
				color = 0xFFFF55;
			}
			case ERROR -> {
				linea = motor.detalle;
				color = 0xFF5555;
			}
			default -> {
				linea = motor.detalle;
				color = 0xAAAAAA;
			}
		}
		g.drawString(mc.font, linea, x, y, color);

		if (motor.estado == MotorVoz.Estado.ESCUCHANDO && !motor.silenciado) {
			int w = 60;
			g.fill(x, y + 11, x + w, y + 13, 0x80000000);
			int lleno = (int) (w * motor.nivel);
			g.fill(x, y + 11, x + lleno, y + 13, motor.nivel > 0.8f ? 0xFFFF5555 : 0xFF55FF55);
		}

		if (!textoHud.isEmpty() && System.currentTimeMillis() < textoHudHasta) {
			String t = textoHud.length() > 60 ? "…" + textoHud.substring(textoHud.length() - 59) : textoHud;
			g.drawString(mc.font, "«" + t + "»", x, y + 17, 0xDDDDDD);
		}
	}

	// ------------------------------------------------------------------ comandos /voz

	private static void registrarComandos() {
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
				ClientCommandManager.literal("voz")
						.executes(ctx -> {
							ctx.getSource().sendFeedback(info());
							return 1;
						})
						.then(ClientCommandManager.literal("probar")
								.then(ClientCommandManager.argument("texto", StringArgumentType.greedyString())
										.executes(ctx -> {
											String t = StringArgumentType.getString(ctx, "texto");
											disparadasEnFrase = 0;
											procesar(t, false);
											if (pendientes.isEmpty()) {
												ctx.getSource().sendFeedback(Component.literal("Ninguna palabra de «" + t + "» coincide con un bloque.").withStyle(ChatFormatting.GRAY));
											}
											return 1;
										})))
						.then(ClientCommandManager.literal("buscar")
								.then(ClientCommandManager.argument("palabra", StringArgumentType.word())
										.executes(ctx -> {
											String p = StringArgumentType.getString(ctx, "palabra");
											Set<ResourceLocation> ids = indice().buscar(p);
											if (ids.isEmpty()) {
												ctx.getSource().sendFeedback(Component.literal("«" + p + "» no borra nada.").withStyle(ChatFormatting.GRAY));
											} else {
												List<String> nombres = new ArrayList<>();
												for (ResourceLocation id : ids) nombres.add(indice().nombre(id));
												ctx.getSource().sendFeedback(Component.literal("«" + p + "» borraría " + ids.size() + " bloques: ")
														.withStyle(ChatFormatting.YELLOW)
														.append(Component.literal(String.join(", ", nombres)).withStyle(ChatFormatting.WHITE)));
											}
											return 1;
										})))
						.then(ClientCommandManager.literal("micros").executes(ctx -> {
							List<String> micros = MotorVoz.listarMicros();
							MutableComponent c = Component.literal("Micrófonos (usa /voz micro <parte del nombre>):").withStyle(ChatFormatting.YELLOW);
							for (String m : micros) c.append(Component.literal("\n - " + m).withStyle(ChatFormatting.WHITE));
							c.append(Component.literal("\nAhora: " + (config.microfono.isBlank() ? "predeterminado" : config.microfono)).withStyle(ChatFormatting.GRAY));
							ctx.getSource().sendFeedback(c);
							return 1;
						}))
						.then(ClientCommandManager.literal("micro")
								.then(ClientCommandManager.argument("nombre", StringArgumentType.greedyString())
										.executes(ctx -> {
											String n = StringArgumentType.getString(ctx, "nombre").trim();
											config.microfono = n.equalsIgnoreCase("predeterminado") ? "" : n;
											config.guardar();
											motor.reiniciarMicro();
											ctx.getSource().sendFeedback(Component.literal("Micrófono: " + (config.microfono.isBlank() ? "predeterminado" : config.microfono)).withStyle(ChatFormatting.GREEN));
											return 1;
										})))
						.then(ClientCommandManager.literal("silenciar").executes(ctx -> {
							alternarSilencio(Minecraft.getInstance());
							return 1;
						}))
						.then(ClientCommandManager.literal("recargar").executes(ctx -> {
							ConfigCliente nueva = ConfigCliente.cargar();
							copiarConfig(nueva);
							reconstruirIndice(Minecraft.getInstance());
							motor.reiniciarMicro();
							ctx.getSource().sendFeedback(Component.literal("Configuración de voz recargada.").withStyle(ChatFormatting.GREEN));
							return 1;
						}))
		));
	}

	/** Copia los valores sobre el objeto existente (el hilo de voz guarda la referencia). */
	private static void copiarConfig(ConfigCliente n) {
		config.microfono = n.microfono;
		config.reaccionRapida = n.reaccionRapida;
		config.modoGramatica = n.modoGramatica;
		config.coincidirGenero = n.coincidirGenero;
		config.palabrasIgnoradas = n.palabrasIgnoradas;
		config.esperaRepeticionMs = n.esperaRepeticionMs;
		config.mostrarHud = n.mostrarHud;
		config.empezarSilenciado = n.empezarSilenciado;
		config.urlModelo = n.urlModelo;
	}

	private static Component info() {
		IndiceBloques idx = indice();
		return Component.literal("[VozBloque] ").withStyle(ChatFormatting.GOLD)
				.append(Component.literal("Estado: " + motor.estado + (motor.detalle.isEmpty() ? "" : " – " + motor.detalle)
						+ "\nMicro: " + motor.microUsado + (motor.silenciado ? " (silenciado)" : "")
						+ "\nBloques en el índice: " + (idx == null ? 0 : idx.numBloques())
						+ (idx != null && !idx.espanolEncontrado() ? " (¡no se encontró el español!)" : "")
						+ "\nComandos: /voz probar <frase> · /voz buscar <palabra> · /voz micros · /voz micro <nombre> · /voz silenciar · /voz recargar")
						.withStyle(ChatFormatting.WHITE));
	}
}
