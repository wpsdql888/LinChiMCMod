package com.dnteam.linchi;

import com.dnteam.linchi.gui.GuiTool;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.CraftingScreen;
import net.minecraft.resources.Identifier;

// HUD：左下材料、右下辅助合成、中间完成提示
// 挂在聊天层前面 —— 继承 hideGui（F1 会自动隐藏），也不会盖住聊天
public class HudRender {

	// AFTER_INIT 在每次 Screen.init() 后触发（包括窗口缩放）。同一个工作台实例
	// 若重复注册鼠标回调，一次点击会被处理多次，表现为“点一下填了好几组”。
	private static final java.util.Set<net.minecraft.client.gui.screens.Screen> HOOKED_SCREENS =
		java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>());

	public static void register() {
		HudElementRegistry.attachElementBefore(
			VanillaHudElements.CHAT,
			Identifier.parse("linchi:hud"),
			HudRender::render
		);
		ScreenEvents.AFTER_INIT.register((mc, screen, w, h) -> {
			if (screen != null) GuiTool.setOpen(true);
			// 工作台那几行“填入”的鼠标事件，用官方 API 挂到这个具体界面实例上
			if (screen instanceof CraftingScreen && HOOKED_SCREENS.add(screen)) {
				ScreenMouseEvents.allowMouseClick(screen).register((scr, e) -> !GongFangZhiYin.onClick(scr, e));
				ScreenMouseEvents.allowMouseScroll(screen).register((scr, mx, my, dx, dy) -> !GongFangZhiYin.onScroll(scr, mx, my, dy));
			}
		});
	}

	private static void render(GuiGraphicsExtractor g, DeltaTracker dt) {
		// 扫描背包交给 tick 事件，渲染这里只读缓存 —— 每帧不该有 IO 和重算
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) return;
		if (com.dnteam.linchi.gui.GuiTool.hasOpenScreen()) return;
		if (ZhuangTai.INS.targets.isEmpty()) return;
		if (PeiZhi.INS.hudKai) drawCaiLiao(g, mc);
		if (PeiZhi.INS.zhuGongKai) drawZhuGong(g, mc);
		if (PeiZhi.INS.hudKai && ZhuangTai.INS.allDone()) drawDone(g, mc);
	}

	// 聊天框在左下，热键栏在正下方。算一下材料栏能放哪儿，别压到它们
	private static int[] placeLeft(Minecraft mc, int rows) {
		int sw = mc.getWindow().getGuiScaledWidth();
		int sh = mc.getWindow().getGuiScaledHeight();
		int w = 152, h = rows * 20;
		int x = 8 + PeiZhi.INS.hudPianYiX;
		// 窄屏时热键栏会伸到左边，往上挪一点
		int hotbarL = (sw - 182) / 2;
		int bottom = sh - 8;
		if (x + w > hotbarL && sh > 200) bottom = sh - 32;
		// 聊天预留高度可以在配置面板里调，默认 96
		int chatTop = sh - Math.max(0, PeiZhi.INS.hudChatJianJu);
		if (bottom > chatTop) bottom = chatTop;
		if (bottom < h + 8) bottom = h + 8;
		return new int[]{ Math.max(0, x), bottom - h, w, h };
	}

	// 右下同理，热键栏右边也可能顶过来
	private static int[] placeRight(Minecraft mc, int rows) {
		int sw = mc.getWindow().getGuiScaledWidth();
		int sh = mc.getWindow().getGuiScaledHeight();
		int w = 172, h = rows * 22;
		int x = sw - w - 8 - PeiZhi.INS.hudPianYiX;
		int bottom = sh - 8;
		int hotbarR = (sw + 182) / 2;
		if (x < hotbarR && sh > 200) bottom = sh - 32;
		int chatTop = sh - Math.max(0, PeiZhi.INS.hudChatJianJu);
		if (x < sw / 2 && bottom > chatTop) bottom = chatTop;
		if (bottom < h + 8) bottom = h + 8;
		return new int[]{ Math.max(0, x), bottom - h, w, h };
	}

	private static void drawCaiLiao(GuiGraphicsExtractor g, Minecraft mc) {
		java.util.List<ZhuangTai.CaiRow> rows = ZhuangTai.INS.hudRows();
		int max = Math.min(Math.max(1, PeiZhi.INS.hudHangShu), rows.size());
		if (max == 0) return;
		int[] box = placeLeft(mc, max);
		int x = box[0], y = box[1], w = box[2];
		g.fill(x - 3, y - 4, x + w, y + box[3] + 2, alphaBg());
		for (int i = 0; i < max; i++) {
			ZhuangTai.CaiRow r = rows.get(i);
			int ry = y + i * 20;
			GuiTool.item(g, r.key, x, ry);
			GuiTool.text(g, shortName(r.key), x + 22, ry + 2, 0xFFFFFFFF);
			String qe = "缺" + r.que;
			GuiTool.text(g, qe, x + w - 4 - mc.font.width(qe), ry + 2, 0xFFFF6666);
		}
	}

	private static void drawZhuGong(GuiGraphicsExtractor g, Minecraft mc) {
		java.util.List<ZhuangTai.BuRow> rows = ZhuangTai.INS.craftRows();
		int max = Math.min(Math.max(1, PeiZhi.INS.zhongJianJu), rows.size());
		if (max == 0) return;
		int[] box = placeRight(mc, max);
		int x = box[0], y = box[1], w = box[2];
		g.fill(x - 3, y - 4, x + w, y + box[3] + 2, alphaBg());
		for (int i = 0; i < max; i++) {
			ZhuangTai.BuRow r = rows.get(i);
			int ry = y + i * 22;
			WuPin wp = ShuJuKu.INS.get(r.bu.ming);
			if (wp != null) GuiTool.item(g, wp.base, x, ry);
			GuiTool.text(g, "第" + (i + 1) + "步 " + r.bu.ming, x + 22, ry + 1, 0xFFFFD54F);
			String rem = "剩" + r.remain;
			GuiTool.text(g, rem, x + w - 4 - mc.font.width(rem), ry + 11, 0xFF7FE0FF);
		}
	}

	// 完成提示放准星上方，别挡着中间
	private static void drawDone(GuiGraphicsExtractor g, Minecraft mc) {
		int sw = mc.getWindow().getGuiScaledWidth();
		int sh = mc.getWindow().getGuiScaledHeight();
		String msg = "材料全部收集完毕 可以进行合成";
		int w = mc.font.width(msg) + 20;
		int x = (sw - w) / 2, y = sh / 2 - 64 + PeiZhi.INS.hudPianYiY;
		g.fill(x, y, x + w, y + 22, 0xE0208020);
		GuiTool.text(g, msg, x + 10, y + 6, 0xFFFFFFFF);
	}

	static int alphaBg() { return (PeiZhi.INS.hudTouMing << 24) | 0x101018; }

	// 物品显示名缓存：getHoverName 走原版翻译，中文客户端出来就是中文名
	private static final java.util.Map<String, String> MING_CACHE = new java.util.HashMap<>();

	static String shortName(String key) {
		if (key == null) return "";
		String n = MING_CACHE.get(key);
		if (n != null) return n;
		n = "";
		try {
			net.minecraft.world.item.ItemStack st = TuBiao.of(key);
			if (!st.isEmpty()) n = st.getHoverName().getString();
		} catch (Throwable ignored) {}
		if (n.isEmpty()) {
			// 兜底：原名逻辑
			if (key.startsWith("minecraft:")) {
				String s = key.substring(10);
				WuPin wp = ShuJuKu.INS.get(s);
				n = wp != null ? wp.ming : s;
			} else n = key;
		}
		MING_CACHE.put(key, n);
		return n;
	}
}
