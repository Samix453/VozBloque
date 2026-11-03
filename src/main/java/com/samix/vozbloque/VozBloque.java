package com.samix.vozbloque;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class VozBloque implements ModInitializer {
	public static final String MOD_ID = "vozbloque";
	public static final Logger LOG = LoggerFactory.getLogger("VozBloque");

	public static ConfigServidor config = new ConfigServidor();

	@Override
	public void onInitialize() {
		ServerLifecycleEvents.SERVER_STARTING.register(server -> {
			config = ConfigServidor.cargar();
			Borrador.vaciar();
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> Borrador.vaciar());
		ServerTickEvents.END_SERVER_TICK.register(Borrador::tick);

		ServerPlayNetworking.registerGlobalReceiver(Red.DICHO, (server, player, handler, buf, responseSender) -> {
			List<Red.Dicho> dichos;
			try {
				dichos = Red.leer(buf);
			} catch (Exception e) {
				LOG.warn("Paquete de voz inválido de {}: {}", player.getName().getString(), e.getMessage());
				return;
			}
			server.execute(() -> Borrador.procesar(server, player, dichos));
		});

		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
				Commands.literal("vozbloque")
						.requires(src -> src.hasPermission(2))
						.executes(VozBloque::estado)
						.then(Commands.literal("activar").executes(ctx -> {
							config.activo = true;
							config.guardar();
							return ok(ctx, "Reto ACTIVADO: lo que digáis se borra.");
						}))
						.then(Commands.literal("pausar").executes(ctx -> {
							config.activo = false;
							config.guardar();
							Borrador.vaciar();
							return ok(ctx, "Reto PAUSADO: podéis hablar tranquilos.");
						}))
						.then(Commands.literal("radio")
								.then(Commands.argument("horizontal", IntegerArgumentType.integer(1, 256))
										.executes(ctx -> radio(ctx, IntegerArgumentType.getInteger(ctx, "horizontal"), config.radioVertical))
										.then(Commands.argument("vertical", IntegerArgumentType.integer(-1, 384))
												.executes(ctx -> radio(ctx, IntegerArgumentType.getInteger(ctx, "horizontal"),
														IntegerArgumentType.getInteger(ctx, "vertical"))))))
						.then(Commands.literal("zona")
								.then(Commands.literal("jugador").executes(ctx -> {
									config.zona = "jugador";
									config.guardar();
									return ok(ctx, "Se borra solo alrededor de quien lo dice.");
								}))
								.then(Commands.literal("todos").executes(ctx -> {
									config.zona = "todos";
									config.guardar();
									return ok(ctx, "Se borra alrededor de TODOS los jugadores.");
								})))
						.then(Commands.literal("recargar").executes(ctx -> {
							config = ConfigServidor.cargar();
							return ok(ctx, "Configuración recargada.");
						}))
		));

		LOG.info("VozBloque cargado: si lo dices, se borra.");
	}

	private static int radio(CommandContext<CommandSourceStack> ctx, int h, int v) {
		config.radioHorizontal = h;
		config.radioVertical = v;
		config.guardar();
		return ok(ctx, "Radio: " + h + " horizontal, " + (v < 0 ? "toda la altura" : v + " vertical") + ".");
	}

	private static int estado(CommandContext<CommandSourceStack> ctx) {
		return ok(ctx, (config.activo ? "Activo" : "Pausado")
				+ " · radio " + config.radioHorizontal + "/" + (config.radioVertical < 0 ? "todo" : config.radioVertical)
				+ " · zona: " + config.zona
				+ "\nComandos: /vozbloque activar|pausar|radio <h> [v]|zona jugador|todos|recargar");
	}

	private static int ok(CommandContext<CommandSourceStack> ctx, String texto) {
		ctx.getSource().sendSuccess(() -> Component.literal("[VozBloque] ").withStyle(ChatFormatting.GOLD)
				.append(Component.literal(texto).withStyle(ChatFormatting.WHITE)), true);
		return 1;
	}
}
