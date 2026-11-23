package com.dnteam.linchi;

import com.dnteam.linchi.gui.GuiTool;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

// 工作台右侧那三行“填入”指引。渲染给 Mixin 调，鼠标走 Fabric 的 ScreenMouseEvents
// 这样不用往 CraftingScreen 里硬塞注入点 —— 那类根本没声明 mouseClicked/mouseScrolled
public class GongFangZhiYin {
	private static final int ROWS = 3;
	private static final int ROW_H = 18;
	private static final int MIN_W = 78;
	private static final int MAX_W = 128;
	private static int page = 0;

	// 合成界面不是永远居中：开了配方书 leftPos 会往右挪。
	// 缓存 AbstractContainerScreen 的 leftPos/topPos，跟着原版界面走，不再按屏幕中点硬算。
	private static java.lang.reflect.Field F_LEFT, F_TOP;

	private static java.lang.reflect.Field findField(Class<?> c, String name) {
		while (c != null) {
			try {
				java.lang.reflect.Field f = c.getDeclaredField(name);
				f.setAccessible(true);
				return f;
			} catch (Throwable ignored) {}
			c = c.getSuperclass();
		}
		return null;
	}

	private static int leftOf(Screen s) {
		try {
			if (F_LEFT == null) F_LEFT = findField(s.getClass(), "leftPos");
			if (F_LEFT != null) return F_LEFT.getInt(s);
		} catch (Throwable ignored) {}
		return (s.width - 176) / 2;
	}

	private static int topOf(Screen s) {
		try {
			if (F_TOP == null) F_TOP = findField(s.getClass(), "topPos");
			if (F_TOP != null) return F_TOP.getInt(s);
		} catch (Throwable ignored) {}
		return (s.height - 166) / 2;
	}

	// 面板位置/宽度每帧现算：Modern UI 会把 GUI 放大，屏幕的逻辑宽高变小，
	// 固定 128 宽经常直接顶出屏幕，表现就是“辅助合成没了”。
	// 先贴着原版界面右边，宽度按剩余空间收；不行再挪到界面下方、上方，最后贴屏幕右边。
	private static final int[] PL = new int[3];

	private static int[] place(Screen s, int rowCount) {
		int w = Math.min(MAX_W, Math.max(MIN_W, s.width - (leftOf(s) + 176) - 8));
		int x = leftOf(s) + 176 + 6;
		int y = topOf(s) + 6;
		if (x + w <= s.width - 2 && y + rowCount * ROW_H <= s.height - 2) {
			PL[0] = x; PL[1] = y; PL[2] = w;
			return PL;
		}
		// 下方
		int bw = Math.min(176, s.width - 8);
		int bx = Math.max(4, (s.width - bw) / 2);
		int by = topOf(s) + 166 + 4;
		if (by + rowCount * ROW_H <= s.height - 2) {
			PL[0] = bx; PL[1] = by; PL[2] = bw;
			return PL;
		}
		// 上方
		int ay = topOf(s) - rowCount * ROW_H - 4;
		if (ay >= 2) {
			PL[0] = bx; PL[1] = ay; PL[2] = bw;
			return PL;
		}
		// 实在没地方就贴右边，压一点界面也比完全看不见强
		PL[0] = Math.max(2, s.width - w - 4);
		PL[1] = Math.max(2, Math.min(y, s.height - rowCount * ROW_H - 2));
		PL[2] = w;
		return PL;
	}

	// 行视图缓存：只在“数据版本”或页码变化时重算。
	// 注意 craftRows() 返回的是同一个 ArrayList 实例（重建时 clear+重填），
	// 比对实例引用永远相等，必须用版本号才能感知数据刷新。
	private static int rowsVer = -1;
	private static int rowsPage = -1;
	private static List<ZhuangTai.BuRow> rowsView = new ArrayList<>();

	private static List<ZhuangTai.BuRow> rows() {
		List<ZhuangTai.BuRow> all = ZhuangTai.INS.craftRows();
		int maxPage = Math.max(0, (all.size() - 1) / ROWS);
		if (page > maxPage) page = maxPage;
		int ver = ZhuangTai.INS.version();
		if (rowsVer == ver && rowsPage == page) return rowsView;
		int from = page * ROWS;
		rowsView = new ArrayList<>(all.subList(from, Math.min(all.size(), from + ROWS)));
		rowsVer = ver;
		rowsPage = page;
		return rowsView;
	}

	public static void draw(GuiGraphicsExtractor g, Screen s, int mx, int my) {
		if (!PeiZhi.INS.zhuGongKai) return;
		List<ZhuangTai.BuRow> rows = rows();
		if (rows.isEmpty()) return;
		int[] pl = place(s, rows.size());
		int x = pl[0], y = pl[1], w = pl[2];
		for (int i = 0; i < rows.size(); i++) {
			ZhuangTai.BuRow r = rows.get(i);
			int ry = y + i * ROW_H;
			boolean hov = GuiTool.hovered(mx, my, x, ry, w, ROW_H - 1);
			// 原版按钮贴图，跟合成界面同一套视觉，Modern UI 缩放也不会走样
			GuiTool.button(g, x, ry, w, ROW_H - 1, hov);
			GuiTool.text(g, fit("填入 " + r.bu.ming + " ×" + r.remain, w - 8), x + 4, ry + 4, 0xFFFFFFFF);
			if (hov) {
				WuPin wp = ShuJuKu.INS.get(r.bu.ming);
				List<String> lines = new ArrayList<>();
				lines.add("目标: " + r.bu.ming + "  还需 " + r.remain);
				PeiFang pf = r.bu.pf != null ? r.bu.pf : (wp == null ? null : wp.gongFang);
				if (pf != null) {
					List<String> grid = pf.jiuGe();
					for (int k = 0; k < 9; k++) {
						if (grid.get(k) != null) lines.add("第" + (k + 1) + "格: " + TuBiao.mingOf(grid.get(k)));
					}
					lines.add("共合成 " + pf.count + " 个");
				}
				lines.add("点击填入一组 / Shift 尽量多填");
				List<Component> comps = new ArrayList<>();
				for (String line : lines) comps.add(Component.literal(line));
				g.setTooltipForNextFrame(Minecraft.getInstance().font, comps, java.util.Optional.empty(), mx, my);
			}
		}
		int total = ZhuangTai.INS.craftRows().size();
		if (total > ROWS) {
			GuiTool.text(g, (page + 1) + "/" + ((total + ROWS - 1) / ROWS) + " 滚轮翻页",
				x, y + rows.size() * ROW_H + 2, 0xFF888888);
		}
	}

	// 按钮字太长就截断，别糊出按钮外
	private static String fit(String txt, int maxW) {
		net.minecraft.client.gui.Font f = Minecraft.getInstance().font;
		if (f.width(txt) <= maxW) return txt;
		StringBuilder b = new StringBuilder();
		for (char c : txt.toCharArray()) {
			if (f.width(b.toString() + c + "..") > maxW) break;
			b.append(c);
		}
		return b + "..";
	}

	// 返回 true 表示这次点击我们吃掉了，别再传给工作台本身
	public static boolean onClick(Screen s, MouseButtonEvent e) {
		if (!PeiZhi.INS.zhuGongKai) return false;
		List<ZhuangTai.BuRow> rows = rows();
		if (rows.isEmpty()) return false;
		int[] pl = place(s, rows.size());
		int x = pl[0], y = pl[1], w = pl[2];
		for (int i = 0; i < rows.size(); i++) {
			int ry = y + i * ROW_H;
			// 整行都是按钮，不再只认最右边那 30px 的“填入”两个字
			if (GuiTool.hovered(e.x(), e.y(), x, ry, w, ROW_H - 1)) {
				TianRu.fill(rows.get(i).bu.ming, e.hasShiftDown() ? 8 : 1);
				return true;
			}
		}
		return false;
	}

	public static boolean onScroll(Screen s, double mx, double my, double dy) {
		if (!PeiZhi.INS.zhuGongKai) return false;
		List<ZhuangTai.BuRow> rows = rows();
		if (rows.isEmpty()) return false;
		int[] pl = place(s, rows.size());
		if (!GuiTool.hovered(mx, my, pl[0], pl[1], pl[2], rows.size() * ROW_H)) return false;
		int total = ZhuangTai.INS.craftRows().size();
		int maxPage = Math.max(0, (total - 1) / ROWS);
		page = Math.max(0, Math.min(maxPage, page + (dy > 0 ? -1 : 1)));
		return true;
	}
}
