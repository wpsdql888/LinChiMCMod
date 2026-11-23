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
		// 按显示名统计。服务器把原版物品都改了名（铁粒叫“铁皮”），
		// 只认注册名会把一堆同名不同物的东西算到一起，这里以名字为准。
		public final Map<String, Integer> byMing = new HashMap<>();
		public int you(String key) { return owned.getOrDefault(key, 0); }
		public int mingYou(String ming) {
			if (ming == null) return 0;
			return byMing.getOrDefault(ShuJuKu.mingKey(ming), 0);
		}
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
			countOne(inv.getItem(i), js);
		}
		// 鼠标上还捏着一叠的情况也数进去，不然刚拖起来的物品会漏掉
		if (mc.player.containerMenu != null) countOne(mc.player.containerMenu.getCarried(), js);
		return js;
	}

	private static void countOne(ItemStack st, JiShu js) {
		if (st.isEmpty()) return;
		String path = pathOf(st);
		if (path == null) return;
		if (!path.contains("shulker_box")) {
			js.owned.merge("minecraft:" + path, st.getCount(), Integer::sum);
			String nm = mingOfStack(st);
			if (!nm.isEmpty()) js.byMing.merge(ShuJuKu.mingKey(nm), st.getCount(), Integer::sum);
		} else {
			scanShulker(st, js);
		}
	}

	private static String mingOfStack(ItemStack st) {
		try {
			return st.getHoverName().getString().trim();
		} catch (Throwable t) {
			return "";
		}
	}

	// 潜影盒里的东西也算进去（套娃不管了，一层够用）
	private static void scanShulker(ItemStack st, JiShu js) {
		try {
			ItemContainerContents box = st.get(DataComponents.CONTAINER);
			if (box == null) return;
			box.nonEmptyItemCopyStream().forEach(inner -> {
				String k = pathOf(inner);
				if (k != null) js.owned.merge("minecraft:" + k, inner.getCount(), Integer::sum);
				String nm = mingOfStack(inner);
				if (!nm.isEmpty()) js.byMing.merge(ShuJuKu.mingKey(nm), inner.getCount(), Integer::sum);
			});
		} catch (Throwable ignored) {}
	}
}
