package com.samix.vozbloque;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

import java.text.NumberFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Busca los bloques dichos alrededor de los jugadores y los va borrando poco a poco. */
public final class Borrador {
	/** Flags de setBlock: avisar a los clientes y no recalcular formas vecinas (evita reacciones en cadena). */
	private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

	private record Trabajo(ServerLevel nivel, LongArrayList posiciones, Set<Block> objetivos, int[] indice) {}

	private static final Deque<Trabajo> COLA = new ArrayDeque<>();

	private Borrador() {}

	public static void procesar(MinecraftServer server, ServerPlayer quien, List<Red.Dicho> dichos) {
		ConfigServidor cfg = VozBloque.config;
		if (!cfg.activo) return;

		Set<Block> protegidos = new ReferenceOpenHashSet<>();
		for (String s : cfg.bloquesProtegidos) {
			ResourceLocation id = ResourceLocation.tryParse(s);
			if (id != null) BuiltInRegistries.BLOCK.getOptional(id).ifPresent(protegidos::add);
		}

		for (Red.Dicho dicho : dichos) {
			Set<Block> objetivos = new ReferenceOpenHashSet<>();
			for (ResourceLocation id : dicho.bloques()) {
				BuiltInRegistries.BLOCK.getOptional(id).ifPresent(b -> {
					if (!b.defaultBlockState().isAir() && !protegidos.contains(b)) objetivos.add(b);
				});
			}
			if (objetivos.isEmpty()) continue;

			List<ServerPlayer> centros = "todos".equalsIgnoreCase(cfg.zona)
					? new ArrayList<>(server.getPlayerList().getPlayers())
					: List.of(quien);

			Object2IntOpenHashMap<Block> cuenta = new Object2IntOpenHashMap<>();
			int total = 0;
			// Agrupar por dimensión
			List<ServerLevel> niveles = new ArrayList<>();
			for (ServerPlayer p : centros) if (!niveles.contains(p.serverLevel())) niveles.add(p.serverLevel());
			for (ServerLevel nivel : niveles) {
				LongOpenHashSet vistos = new LongOpenHashSet();
				LongArrayList lista = new LongArrayList();
				for (ServerPlayer p : centros) {
					if (p.serverLevel() != nivel) continue;
					escanear(nivel, p.blockPosition(), cfg, objetivos, vistos, lista, cuenta);
				}
				if (!lista.isEmpty()) {
					COLA.add(new Trabajo(nivel, lista, objetivos, new int[]{0}));
					total += lista.size();
				}
			}

			anunciar(server, quien, dicho.palabra(), cuenta, total);

			if (total > 0 && cfg.sonido) {
				for (ServerPlayer p : centros) {
					p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.ENDERMAN_TELEPORT,
							SoundSource.PLAYERS, 0.7f, 1.4f);
				}
			}
		}
	}

	private static void escanear(ServerLevel nivel, BlockPos centro, ConfigServidor cfg, Set<Block> objetivos,
								 LongOpenHashSet vistos, LongArrayList lista, Object2IntOpenHashMap<Block> cuenta) {
		int rh = Math.max(1, cfg.radioHorizontal);
		int minX = centro.getX() - rh, maxX = centro.getX() + rh;
		int minZ = centro.getZ() - rh, maxZ = centro.getZ() + rh;
		int minY, maxY;
		if (cfg.radioVertical < 0) {
			minY = nivel.getMinBuildHeight();
			maxY = nivel.getMaxBuildHeight() - 1;
		} else {
			minY = Math.max(nivel.getMinBuildHeight(), centro.getY() - cfg.radioVertical);
			maxY = Math.min(nivel.getMaxBuildHeight() - 1, centro.getY() + cfg.radioVertical);
		}

		BlockPos.MutableBlockPos mut = new BlockPos.MutableBlockPos();
		for (int cx = minX >> 4; cx <= maxX >> 4; cx++) {
			for (int cz = minZ >> 4; cz <= maxZ >> 4; cz++) {
				LevelChunk chunk = nivel.getChunkSource().getChunkNow(cx, cz);
				if (chunk == null) continue;
				LevelChunkSection[] secciones = chunk.getSections();
				for (int i = 0; i < secciones.length; i++) {
					LevelChunkSection sec = secciones[i];
					if (sec == null || sec.hasOnlyAir()) continue;
					int baseY = chunk.getSectionYFromSectionIndex(i) << 4;
					if (baseY + 15 < minY || baseY > maxY) continue;
					if (!sec.maybeHas(st -> objetivos.contains(st.getBlock()))) continue;

					int x0 = Math.max(minX, cx << 4), x1 = Math.min(maxX, (cx << 4) + 15);
					int z0 = Math.max(minZ, cz << 4), z1 = Math.min(maxZ, (cz << 4) + 15);
					int y0 = Math.max(minY, baseY), y1 = Math.min(maxY, baseY + 15);
					for (int y = y0; y <= y1; y++) {
						for (int z = z0; z <= z1; z++) {
							for (int x = x0; x <= x1; x++) {
								BlockState st = sec.getBlockState(x & 15, y & 15, z & 15);
								Block b = st.getBlock();
								if (!objetivos.contains(b)) continue;
								long key = mut.set(x, y, z).asLong();
								if (vistos.add(key)) {
									lista.add(key);
									cuenta.addTo(b, 1);
								}
							}
						}
					}
				}
			}
		}
	}

	private static void anunciar(MinecraftServer server, ServerPlayer quien, String palabra,
								 Object2IntOpenHashMap<Block> cuenta, int total) {
		NumberFormat nf = NumberFormat.getIntegerInstance(new Locale("es", "ES"));
		MutableComponent msg = Component.literal("[VozBloque] ").withStyle(ChatFormatting.GOLD)
				.append(quien.getDisplayName().copy().withStyle(ChatFormatting.WHITE))
				.append(Component.literal(" ha dicho ").withStyle(ChatFormatting.GRAY))
				.append(Component.literal("«" + palabra + "»").withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD));

		if (total == 0) {
			msg.append(Component.literal(" → no había nada cerca").withStyle(ChatFormatting.GRAY));
		} else {
			msg.append(Component.literal(" → ").withStyle(ChatFormatting.GRAY));
			List<Object2IntMap.Entry<Block>> orden = new ArrayList<>(cuenta.object2IntEntrySet());
			orden.sort(Comparator.comparingInt((Object2IntMap.Entry<Block> e) -> e.getIntValue()).reversed());
			int mostrar = Math.min(5, orden.size());
			for (int i = 0; i < mostrar; i++) {
				if (i > 0) msg.append(Component.literal(", ").withStyle(ChatFormatting.GRAY));
				Object2IntMap.Entry<Block> e = orden.get(i);
				msg.append(Component.translatable(e.getKey().getDescriptionId()).withStyle(ChatFormatting.AQUA));
				msg.append(Component.literal(" ×" + nf.format(e.getIntValue())).withStyle(ChatFormatting.DARK_AQUA));
			}
			if (orden.size() > mostrar) {
				msg.append(Component.literal(" y " + (orden.size() - mostrar) + " tipos más").withStyle(ChatFormatting.GRAY));
			}
			msg.append(Component.literal(" (" + nf.format(total) + " bloques)").withStyle(ChatFormatting.RED));
		}
		server.getPlayerList().broadcastSystemMessage(msg, false);
	}

	/** Se llama cada tick del servidor: borra un lote de bloques pendientes. */
	public static void tick(MinecraftServer server) {
		int presupuesto = Math.max(500, VozBloque.config.bloquesPorTick);
		BlockState aire = Blocks.AIR.defaultBlockState();
		BlockPos.MutableBlockPos mut = new BlockPos.MutableBlockPos();
		while (presupuesto > 0 && !COLA.isEmpty()) {
			Trabajo t = COLA.peek();
			int[] idx = t.indice();
			LongArrayList pos = t.posiciones();
			while (presupuesto > 0 && idx[0] < pos.size()) {
				mut.set(BlockPos.getX(pos.getLong(idx[0])), BlockPos.getY(pos.getLong(idx[0])), BlockPos.getZ(pos.getLong(idx[0])));
				idx[0]++;
				presupuesto--;
				if (!t.nivel().isLoaded(mut)) continue;
				BlockState actual = t.nivel().getBlockState(mut);
				if (t.objetivos().contains(actual.getBlock())) {
					t.nivel().setBlock(mut, aire, FLAGS);
				}
			}
			if (idx[0] >= pos.size()) COLA.poll();
		}
	}

	public static void vaciar() {
		COLA.clear();
	}
}
