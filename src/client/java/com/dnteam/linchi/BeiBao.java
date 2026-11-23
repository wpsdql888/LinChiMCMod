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

	// 返回物品注册表 id 的 path（小写），空栈返回 null。
	// 一次查询既判断是不是潜影盒、又生成统计用的 key，避免每个物品查两次注册表。
	private static String pathOf(ItemStack st) {
		if (st.isEmpty()) return null;
		try {
			Identifier id = BuiltInRegistries.ITEM.getKey(st.getItem());
			return id == null ? null : id.getPath();
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
			String path = pathOf(st);
			if (path == null) continue;
			if (!path.contains("shulker_box")) {
				js.owned.merge("minecraft:" + path, st.getCount(), Integer::sum);
			} else {
				scanShulker(st, js);
			}
		}
		return js;
	}

	// 潜影盒里的东西也算进去（套娃不管了，一层够用）
	private static void scanShulker(ItemStack st, JiShu js) {
		try {
			ItemContainerContents box = st.get(DataComponents.CONTAINER);
			if (box == null) return;
			box.nonEmptyItemCopyStream().forEach(inner -> {
				String k = pathOf(inner);
				if (k != null) js.owned.merge("minecraft:" + k, inner.getCount(), Integer::sum);
			});
		} catch (Throwable ignored) {}
	}
}
