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
		// 直接查当前有没有界面，比靠多处手动维护的 screenOpen 布尔可靠：
		// 后者任何一处忘了 setOpen(false) 都会让 HUD 永久消失。
		if (JianRong.currentScreen() != null) return;
		if (ZhuangTai.INS.targets.isEmpty()) return;
		// 整体缩到 0.75：原来按 1:1 画，GUI 缩放一高就糊半屏。
		// 缩放后可用坐标变大，位置要按“虚拟宽高”算。
		g.pose().pushMatrix();
		g.pose().scale(HUD_SUO, HUD_SUO);
		int vw = (int) (mc.getWindow().getGuiScaledWidth() / HUD_SUO);
		int vh = (int) (mc.getWindow().getGuiScaledHeight() / HUD_SUO);
		if (PeiZhi.INS.hudKai) drawCaiLiao(g, mc, vw, vh);
		if (PeiZhi.INS.zhuGongKai) drawZhuGong(g, mc, vw, vh);
		if (PeiZhi.INS.hudKai && ZhuangTai.INS.allDone()) drawDone(g, mc, vw, vh);
		g.pose().popMatrix();
	}

	private static final float HUD_SUO = 0.75f;

	// 布局结果缓冲：HUD 每帧算位置，复用数组避免每帧 new int[4]
	private static final int[] BOX_L = new int[4], BOX_R = new int[4];

	// 聊天框在左下，热键栏在正下方。算一下材料栏能放哪儿，别压到它们
	private static int[] placeLeft(int sw, int sh, int rows) {
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
		BOX_L[0] = Math.max(0, x); BOX_L[1] = bottom - h; BOX_L[2] = w; BOX_L[3] = h;
		return BOX_L;
	}

	// 右下同理，热键栏右边也可能顶过来
	private static int[] placeRight(int sw, int sh, int rows) {
		int w = 172, h = rows * 22;
		int x = sw - w - 8 - PeiZhi.INS.hudPianYiX;
		int bottom = sh - 8;
		int hotbarR = (sw + 182) / 2;
		if (x < hotbarR && sh > 200) bottom = sh - 32;
		int chatTop = sh - Math.max(0, PeiZhi.INS.hudChatJianJu);
		if (x < sw / 2 && bottom > chatTop) bottom = chatTop;
		if (bottom < h + 8) bottom = h + 8;
		BOX_R[0] = Math.max(0, x); BOX_R[1] = bottom - h; BOX_R[2] = w; BOX_R[3] = h;
		return BOX_R;
	}

	private static void drawCaiLiao(GuiGraphicsExtractor g, Minecraft mc, int sw, int sh) {
		java.util.List<ZhuangTai.CaiRow> rows = ZhuangTai.INS.hudRows();
		int max = Math.min(Math.max(1, PeiZhi.INS.hudHangShu), rows.size());
		if (max == 0) return;
		int[] box = placeLeft(sw, sh, max);
		int x = box[0], y = box[1], w = box[2];
		g.fill(x - 3, y - 4, x + w, y + box[3] + 2, alphaBg());
		for (int i = 0; i < max; i++) {
			ZhuangTai.CaiRow r = rows.get(i);
			int ry = y + i * 20;
			GuiTool.item(g, r.key, x, ry);
			GuiTool.text(g, shortName(r.key), x + 22, ry + 2, 0xFFFFFFFF);
			String qe = String.valueOf(r.que);
			GuiTool.text(g, qe, x + w - 4 - textWidth(mc, qe), ry + 2, 0xFFFF6666);
		}
	}

	private static void drawZhuGong(GuiGraphicsExtractor g, Minecraft mc, int sw, int sh) {
		java.util.List<ZhuangTai.BuRow> rows = ZhuangTai.INS.craftRows();
		int max = Math.min(Math.max(1, PeiZhi.INS.zhongJianJu), rows.size());
		if (max == 0) return;
		int[] box = placeRight(sw, sh, max);
		int x = box[0], y = box[1], w = box[2];
		g.fill(x - 3, y - 4, x + w, y + box[3] + 2, alphaBg());
		for (int i = 0; i < max; i++) {
			ZhuangTai.BuRow r = rows.get(i);
			int ry = y + i * 22;
			WuPin wp = ShuJuKu.INS.get(r.bu.ming);
			// 传中文名，别传 base：base 会把魔法物品画成借用的原版物品，光效也丢了
			if (wp != null) GuiTool.item(g, wp.ming, x, ry);
			GuiTool.text(g, "第" + (i + 1) + "步 " + r.bu.ming, x + 22, ry + 1, 0xFFFFD54F);
			String rem = "剩" + r.remain;
			GuiTool.text(g, rem, x + w - 4 - textWidth(mc, rem), ry + 11, 0xFF7FE0FF);
		}
	}

	// 完成提示放准星上方，别挡着中间
	private static final String DONE_MSG = "材料全部收集完毕 可以进行合成";
	private static int doneMsgWidth = -1; // 字体固定，宽度算一次就够
	private static void drawDone(GuiGraphicsExtractor g, Minecraft mc, int sw, int sh) {
		String msg = DONE_MSG;
		if (doneMsgWidth < 0) doneMsgWidth = mc.font.width(msg);
		int w = doneMsgWidth + 20;
		int x = (sw - w) / 2, y = sh / 2 - 64 + PeiZhi.INS.hudPianYiY;
		g.fill(x, y, x + w, y + 22, 0xE0208020);
		GuiTool.text(g, msg, x + 10, y + 6, 0xFFFFFFFF);
	}

	// "缺N" / "剩N" 每帧要量宽度对齐右侧，数字范围小，缓存起来别每帧量字体
	private static final java.util.Map<String, Integer> WIDTH_CACHE = new java.util.HashMap<>();
	private static int textWidth(Minecraft mc, String s) {
		Integer w = WIDTH_CACHE.get(s);
		if (w == null) { w = mc.font.width(s); WIDTH_CACHE.put(s, w); }
		return w;
	}

	static int alphaBg() { return (PeiZhi.INS.hudTouMing << 24) | 0x101018; }

	// 物品显示名缓存：getHoverName 走原版翻译，中文客户端出来就是中文名
	private static final java.util.Map<String, String> MING_CACHE = new java.util.HashMap<>();

	static String shortName(String key) {
		if (key == null) return "";
		return TuBiao.mingOf(key);
		/*
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
		*/
	}
}
