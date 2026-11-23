package com.dnteam.linchi;

import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.ResultSlot;
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
			// 结果槽不是输入格，排掉更稳，免得在某些容器实现里顶掉第 1 格
			if (s instanceof ResultSlot) continue;
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
					// 服务器物品全是改过名的，认名字不认注册名
					String wantMing = TuBiao.mingOf(key);
					int bagSlot = findBagSlot(mc.player, wantMing);
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

	// 新逻辑：一次尽量填满。目标次数按“还差多少个成品 / 单次产出”算，
	// 每个材料格最多 64 个，背包有多少填多少，不提示。
	// 关键：26.x 直接调 menu.clicked 只改本地，不发包，服务端看不到 —— 之前就是这么幽灵填入的。
	public static void fillMax(QiuJie.Bu bu) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.level == null || bu == null || mc.gameMode == null) return;
		WuPin wp = ShuJuKu.INS.get(bu.ming);
		if (wp == null) return;
		PeiFang pf = bu.pf != null ? bu.pf : wp.gongFang;
		// 魔镜配方不是原版工作台，别硬填
		if (pf == null || pf.mirror) return;
		AbstractContainerMenu menu = mc.player.containerMenu;
		if (menu == null) return;
		// 手上还捏着东西的时候不抢，免得把玩家手里的物品塞进工作台
		if (!menu.getCarried().isEmpty()) return;

		int[] cells = gridCells(menu);
		if (cells == null) return;

		List<String> grid = pf.jiuGe();
		String[] wantName = new String[9];
		int[] haveCnt = new int[9];
		int[] cellCap = new int[9];
		Map<String, Integer> perCraft = new LinkedHashMap<>();
		int maxCrafts = 64;

		for (int i = 0; i < 9; i++) {
			String key = grid.get(i);
			if (key == null) continue;
			String nm = TuBiao.mingOf(key);
			wantName[i] = nm;
			ItemStack icon = TuBiao.of(key);
			int cap = Math.min(64, icon.isEmpty() ? 64 : icon.getMaxStackSize());
			cellCap[i] = cap;
			perCraft.merge(nm, 1, Integer::sum);

			ItemStack cur = menu.slots.get(cells[i]).getItem();
			if (!cur.isEmpty()) {
				// 格子里已经是别的东西，整个放弃，不替玩家清格子
				if (!hoverName(cur).equals(nm)) return;
				haveCnt[i] = cur.getCount();
			}
			maxCrafts = Math.min(maxCrafts, cap);
		}
		if (perCraft.isEmpty()) return;

		// 成品还差多少个：用实时背包，别用右侧那行可能还没刷新的数字
		int owned = BeiBao.scan().mingYou(bu.ming);
		int remainItems = Math.max(0, bu.need - owned);
		int perOut = Math.max(1, pf.count);
		int crafts = (remainItems + perOut - 1) / perOut;
		crafts = Math.min(crafts, maxCrafts);
		if (crafts <= 0) return;

		// 背包里（不算潜影盒内容）够不够，不够就按现有材料能撑几次
		for (Map.Entry<String, Integer> e : perCraft.entrySet()) {
			String nm = e.getKey();
			int already = 0;
			for (int i = 0; i < 9; i++) if (nm.equals(wantName[i])) already += haveCnt[i];
			int avail = bagCount(menu, mc.player, nm);
			crafts = Math.min(crafts, (already + avail) / e.getValue());
		}
		if (crafts <= 0) return;

		for (int i = 0; i < 9; i++) {
			if (wantName[i] == null) continue;
			int want = Math.min(cellCap[i], crafts);
			int delta = want - haveCnt[i];
			if (delta <= 0) continue;
			moveIn(menu, mc.player, cells[i], wantName[i], delta);
			if (mc.player.containerMenu != menu) return; // 界面被关掉了就停
		}
	}

	// 3×3 输入格：排除结果槽，按 containerSlot 映射到 handle 序号
	private static int[] gridCells(AbstractContainerMenu menu) {
		int[] cells = new int[9];
		Arrays.fill(cells, -1);
		for (Slot s : menu.slots) {
			if (s instanceof ResultSlot) continue;
			if (s.container instanceof Container c && c.getContainerSize() == 9 && s.getContainerSlot() >= 0 && s.getContainerSlot() < 9) {
				cells[s.getContainerSlot()] = s.index;
			}
		}
		for (int x : cells) if (x < 0) return null;
		return cells;
	}

	private static String hoverName(ItemStack st) {
		try {
			return st.getHoverName().getString().trim();
		} catch (Throwable t) {
			return "";
		}
	}

	// 背包本体里同名物品的总数，潜影盒里的不算
	private static int bagCount(AbstractContainerMenu menu, Player p, String wantMing) {
		int n = 0;
		for (Slot s : menu.slots) {
			if (s.container != p.getInventory()) continue;
			ItemStack st = s.getItem();
			if (st.isEmpty()) continue;
			if (hoverName(st).equals(wantMing)) n += st.getCount();
		}
		return n;
	}

	// 往一个格子里补 delta 个。整叠能放下就左键一次，放不满就右键逐个放。
	private static void moveIn(AbstractContainerMenu menu, Player p, int target, String wantMing, int delta) {
		int guard = 0;
		while (delta > 0 && guard++ < 2048) {
			int bag = findBagSlot(p, wantMing);
			if (bag < 0) return;
			sendClick(menu, bag, 0, ContainerInput.PICKUP);
			if (menu.getCarried().isEmpty()) return;

			ItemStack tgt = menu.slots.get(target).getItem();
			int before = tgt.isEmpty() ? 0 : tgt.getCount();
			int cap = Math.min(64, menu.getCarried().getMaxStackSize());
			int room = cap - before;
			if (room <= 0) { sendClick(menu, bag, 0, ContainerInput.PICKUP); return; }

			int place = Math.min(delta, Math.min(menu.getCarried().getCount(), room));
			if (place >= menu.getCarried().getCount()) {
				sendClick(menu, target, 0, ContainerInput.PICKUP);
			} else {
				for (int k = 0; k < place; k++) sendClick(menu, target, 1, ContainerInput.PICKUP);
			}

			ItemStack after = menu.slots.get(target).getItem();
			int moved = (after.isEmpty() ? 0 : after.getCount()) - before;
			if (!menu.getCarried().isEmpty()) sendClick(menu, bag, 0, ContainerInput.PICKUP);
			if (moved <= 0) return;
			delta -= moved;
		}
	}

	// 走官方发包路径；menu.clicked 在 26.x 只做本地模拟
	private static void sendClick(AbstractContainerMenu menu, int slot, int button, ContainerInput input) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.gameMode == null) return;
		mc.gameMode.handleContainerInput(menu.containerId, slot, button, input, mc.player);
	}

	// 只找背包本体，不去翻潜影盒 —— 潜影盒里的按“缺料”处理，静默失败
	// 匹配用显示名：minecraft:iron_ingot 会翻成“铁锭”，魔法物品直接用中文名
	private static int findBagSlot(Player p, String wantMing) {
		if (wantMing == null || wantMing.isEmpty()) return -1;
		AbstractContainerMenu menu = p.containerMenu;
		for (Slot s : menu.slots) {
			if (s.container != p.getInventory()) continue;
			ItemStack st = s.getItem();
			if (st.isEmpty()) continue;
			String nm;
			try {
				nm = st.getHoverName().getString().trim();
			} catch (Throwable t) {
				continue;
			}
			if (nm.equals(wantMing.trim())) return s.index;
		}
		return -1;
	}

	/*
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
	*/
}
