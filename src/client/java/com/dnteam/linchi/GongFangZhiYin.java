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
	private static final int ROW_H = 22;
	private static final int PANEL_W = 122;
	private static int page = 0;

	public static int panelX(Screen s) { return s.width / 2 + 96; }
	public static int panelY(Screen s) { return s.height / 2 - 79 + 4; }

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
		int x = panelX(s), y = panelY(s);
		for (int i = 0; i < rows.size(); i++) {
			ZhuangTai.BuRow r = rows.get(i);
			int ry = y + i * ROW_H;
			boolean hov = GuiTool.hovered(mx, my, x, ry, PANEL_W, ROW_H - 1);
			GuiTool.panel(g, x, ry, PANEL_W, ROW_H - 1, hov ? 0xD0202838 : 0xD0101018);
			WuPin wp = ShuJuKu.INS.get(r.bu.ming);
			if (wp != null) GuiTool.item(g, wp.base, x + 1, ry + 2);
			GuiTool.text(g, r.bu.ming + "x" + r.remain, x + 22, ry + 6, 0xFFFFD54F);
			boolean fillHov = GuiTool.hovered(mx, my, x + PANEL_W - 32, ry, 30, ROW_H - 1);
			GuiTool.text(g, "填入", x + PANEL_W - 30, ry + 6, fillHov ? 0xFFFFFFFF : 0xFF7FE0FF);
			if (fillHov && wp != null) {
				List<String> lines = new ArrayList<>();
				lines.add("目标: " + wp.ming + "  还需 " + r.remain);
				PeiFang pf = r.bu.pf != null ? r.bu.pf : wp.gongFang;
				if (pf != null) {
					List<String> grid = pf.jiuGe();
					for (int k = 0; k < 9; k++) {
						if (grid.get(k) != null) lines.add("第" + (k + 1) + "格: " + grid.get(k));
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
				x, y + ROWS * ROW_H + 2, 0xFF888888);
		}
	}

	// 返回 true 表示这次点击我们吃掉了，别再传给工作台本身
	public static boolean onClick(Screen s, MouseButtonEvent e) {
		if (!PeiZhi.INS.zhuGongKai) return false;
		int x = panelX(s), y = panelY(s);
		List<ZhuangTai.BuRow> rows = rows();
		for (int i = 0; i < rows.size(); i++) {
			int ry = y + i * ROW_H;
			if (GuiTool.hovered(e.x(), e.y(), x + PANEL_W - 32, ry, 30, ROW_H - 1)) {
				TianRu.fill(rows.get(i).bu.ming, e.hasShiftDown() ? 8 : 1);
				return true;
			}
		}
		return false;
	}

	public static boolean onScroll(Screen s, double mx, double my, double dy) {
		if (!PeiZhi.INS.zhuGongKai) return false;
		if (!GuiTool.hovered(mx, my, panelX(s), panelY(s), PANEL_W, ROWS * ROW_H)) return false;
		int total = ZhuangTai.INS.craftRows().size();
		int maxPage = Math.max(0, (total - 1) / ROWS);
		page = Math.max(0, Math.min(maxPage, page + (dy > 0 ? -1 : 1)));
		return true;
	}
}
