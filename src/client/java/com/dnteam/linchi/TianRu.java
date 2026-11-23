package com.dnteam.linchi;

import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.*;

// 填入逻辑：把配方的材料摆进工作台 3x3 槽位
// 走 menu.clicked 而不是直接 set —— 这样服务端能收到点击包，多人游戏里不会“看着填了其实没有”
public class TianRu {

	public static void fill(String targetMing) { fill(targetMing, 1); }

	// sets 是填入组数：每轮往 3x3 每格放 1 个，跑 sets 轮 = 能连续合成 sets 次。
	// 缺料的格子静默跳过，不提示；一组都填不进就提前停。
	public static void fill(String targetMing, int sets) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.level == null) return;
		WuPin wp = ShuJuKu.INS.get(targetMing);
		if (wp == null || wp.gongFang == null) return;
		List<String> grid = wp.gongFang.jiuGe();
		AbstractContainerMenu menu = mc.player.containerMenu;
		if (menu == null) return;

		int[] craftSlots = new int[9];
		Arrays.fill(craftSlots, -1);
		for (Slot s : menu.slots) {
			if (s.container instanceof Container c && c.getContainerSize() == 9 && s.getContainerSlot() >= 0 && s.getContainerSlot() < 9) {
				craftSlots[s.getContainerSlot()] = s.index;
			}
		}
		for (int x : craftSlots) if (x < 0) return;

		try {
			// PICKUP + button=1（右键）= 一次只放 1 个到目标格。
			// 之前用左键(button=0)会把背包里整叠(最多64个)全塞进同一个合成格：
			// 既浪费材料，又会因为第一格拿走了那一叠、导致共用同种材料的后续格子填不齐。
			// 合成网格每格只需要 1 个物品，右键逐个放才符合原版/JEI 的习惯。
			for (int round = 0; round < Math.max(1, sets); round++) {
				boolean moved = false;
				for (int i = 0; i < 9; i++) {
					String key = grid.get(i);
					if (key == null) continue;
					String base = ShuJuKu.INS.baseOf(key);
					if (base == null) continue;
					int bagSlot = findBagSlot(mc.player, base);
					if (bagSlot < 0) continue; // 缺料就直接跳过，按需求不提示
					int target = craftSlots[i];
					menu.clicked(bagSlot, 0, ContainerInput.PICKUP, mc.player); // 拾起一叠
					menu.clicked(target, 1, ContainerInput.PICKUP, mc.player);  // 右键放 1 个
					// 手上剩下的塞回背包那个槽，下一轮 / 下一格还能接着用
					if (!menu.getCarried().isEmpty()) menu.clicked(bagSlot, 0, ContainerInput.PICKUP, mc.player);
					moved = true;
				}
				if (!moved) break;
			}
		} catch (Throwable t) {
			LinChiMod.LOG.debug("填入中断: {}", t.getMessage());
		}
	}

	// 只找背包本体，不去翻潜影盒 —— 潜影盒里的按“缺料”处理，静默失败
	private static int findBagSlot(Player p, String base) {
		String id = base.contains(":") ? base.toLowerCase(Locale.ROOT) : "minecraft:" + base.toLowerCase(Locale.ROOT);
		AbstractContainerMenu menu = p.containerMenu;
		Item want;
		try {
			want = BuiltInRegistries.ITEM.getOptional(Identifier.parse(id)).orElse(null);
		} catch (Throwable t) { return -1; }
		if (want == null) return -1;
		for (Slot s : menu.slots) {
			if (s.container != p.getInventory()) continue;
			ItemStack st = s.getItem();
			if (!st.isEmpty() && st.getItem() == want) return s.index;
		}
		return -1;
	}
}
