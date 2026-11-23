package com.dnteam.linchi;

import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;

import java.util.*;

// 扫描背包 + 潜影盒，统计每种材料拥有多少
public class BeiBao {

	public static class JiShu {
		public final Map<String, Integer> owned = new HashMap<>();
		public int you(String key) { return owned.getOrDefault(key, 0); }
		public int que(String key, int need) { return Math.max(0, need - you(key)); }
	}

	private static String keyOf(ItemStack st) {
		if (st.isEmpty()) return null;
		try {
			Identifier id = BuiltInRegistries.ITEM.getKey(st.getItem());
			if (id == null) return null;
			return "minecraft:" + id.getPath();
		} catch (Exception e) {
			return null;
		}
	}

	public static JiShu scan() {
		JiShu js = new JiShu();
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) return js;
		Inventory inv = mc.player.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack st = inv.getItem(i);
			if (st.isEmpty()) continue;
			String key = keyOf(st);
			if (key != null && !key.contains("shulker_box")) {
				js.owned.merge(key, st.getCount(), Integer::sum);
			}
			scanShulker(st, js);
		}
		return js;
	}

	// 潜影盒里的东西也算进去（套娃不管了，一层够用）
	private static void scanShulker(ItemStack st, JiShu js) {
		try {
			Identifier id = BuiltInRegistries.ITEM.getKey(st.getItem());
			if (id == null || !id.getPath().endsWith("shulker_box")) return;
			ItemContainerContents box = st.get(DataComponents.CONTAINER);
			if (box == null) return;
			box.nonEmptyItemCopyStream().forEach(inner -> {
				String k = keyOf(inner);
				if (k != null) js.owned.merge(k, inner.getCount(), Integer::sum);
			});
		} catch (Throwable ignored) {}
	}
}
