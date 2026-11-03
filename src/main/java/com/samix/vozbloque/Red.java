package com.samix.vozbloque;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/** Paquete cliente → servidor: "he dicho estas palabras, que corresponden a estos bloques". */
public final class Red {
	public static final ResourceLocation DICHO = new ResourceLocation(VozBloque.MOD_ID, "dicho");

	public static final int MAX_PALABRAS = 32;
	public static final int MAX_BLOQUES = 4096;

	public record Dicho(String palabra, List<ResourceLocation> bloques) {}

	private Red() {}

	public static void escribir(FriendlyByteBuf buf, List<Dicho> dichos) {
		int n = Math.min(dichos.size(), MAX_PALABRAS);
		buf.writeVarInt(n);
		for (int i = 0; i < n; i++) {
			Dicho d = dichos.get(i);
			buf.writeUtf(d.palabra(), 64);
			int m = Math.min(d.bloques().size(), MAX_BLOQUES);
			buf.writeVarInt(m);
			for (int j = 0; j < m; j++) buf.writeResourceLocation(d.bloques().get(j));
		}
	}

	public static List<Dicho> leer(FriendlyByteBuf buf) {
		int n = buf.readVarInt();
		if (n < 0 || n > MAX_PALABRAS) throw new IllegalArgumentException("Demasiadas palabras: " + n);
		List<Dicho> out = new ArrayList<>(n);
		for (int i = 0; i < n; i++) {
			String palabra = buf.readUtf(64);
			int m = buf.readVarInt();
			if (m < 0 || m > MAX_BLOQUES) throw new IllegalArgumentException("Demasiados bloques: " + m);
			List<ResourceLocation> ids = new ArrayList<>(m);
			for (int j = 0; j < m; j++) ids.add(buf.readResourceLocation());
			out.add(new Dicho(palabra, ids));
		}
		return out;
	}
}
