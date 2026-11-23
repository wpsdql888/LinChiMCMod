package com.dnteam.linchi.gui;

import com.dnteam.linchi.*;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.*;

// 材料板：N 打开，屏幕中段非全屏
// 交互照着 JEI 的路子来：左键看配方，右键看用途，滚轮翻页，悬停出提示
public class CaiLiaoBanScreen extends Screen {
	private final Screen parent;
	private int px, py, pw, ph, third;
	private int listTop, listBottom, matY, btnY;
	// Modern UI 会把 GUI 放大，逻辑高度可能只剩两百出头，固定 chrome 会挤掉列表
	private int topY, titleY, searchY, searchH, btnH, barH, shortScr;
	// 行高压到 18，图标 12px。原来 22px 一行 + 16px 图标，GUI 缩放一高就占满半屏
	private static final int ROW = 18;
	private static final int RECIPE_W = 210;
	private static final int TOGGLE_W = 34;
	// 配方 3×3 的格子尺寸/起点，画和点都用这套，避免两处算错
	private int gridCell = 34, gridX0, gridY0;

	private final List<QiuJie.Target> added = new ArrayList<>();
	private WuPin selLeft, selRight;
	// 左栏当前选中的 key；基础材料是 minecraft:xxx，不一定能查到 WuPin
	private String selLeftKey;

	// 搜索和滚动，这四样是分开的，互不串台
	private EditBox searchLeft, searchRight;
	private String lastLeftQ = "", lastRightQ = "\u0000";
	// 左栏现在是“目标 + 可合成中间物 + 基础材料”的总表
	private static class MatRow {
		String key;
		String disp;
		int need;
		boolean isTarget;
		boolean isCraft;
		boolean craftable;
	}
	private List<MatRow> leftView = new ArrayList<>();
	private boolean leftDirty = false;
	// 自己判双击，不信原版传进来的 doubleClick（部分环境/鼠标会把单击也标成双击）
	private long lastLeftAt, lastRightAt;
	private String lastLeftKey, lastRightKey;
	private int lastLeftY;
	private static final long DBL_MS = 280;
	private List<WuPin> rightView = Collections.emptyList();
	private int leftScroll = 0, rightScroll = 0, matScroll = 0;
	private int lastMx = 0, lastMy = 0;

	// 配方可以一层层往里点，历史用栈存，Backspace 退回上一层
	private boolean showRecipe = false;
	private boolean recipeFromLeft = false;
	private final Deque<WuPin> recipeHistory = new ArrayDeque<>();
	private WuPin recipeTarget;
	private int recipePage = 0;

	// 右键某材料时，右侧切成“谁用到了它”
	private String usesKey;

	private List<QiuJie.Cai> curBase = new ArrayList<>();

	public CaiLiaoBanScreen(Screen parent) {
		super(Component.literal("LinsMagic 材料板"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		pw = Math.min(980, this.width - 24);
		ph = Math.min(620, this.height - 24);
		px = (this.width - pw) / 2;
		py = (this.height - ph) / 2;
		third = Math.max(150, pw / 3);
		// 逻辑高度不够时整套 chrome 一起缩，不然 Modern UI 放大后列表只剩一两行
		shortScr = this.height < 330 ? 1 : 0;
		topY = py + (shortScr == 1 ? 4 : 8);
		btnH = shortScr == 1 ? 18 : 20;
		titleY = py + (shortScr == 1 ? 24 : 32);
		searchY = py + (shortScr == 1 ? 38 : 50);
		searchH = shortScr == 1 ? 18 : 20;
		listTop = searchY + searchH + 4;
		btnY = py + ph - (shortScr == 1 ? 24 : 26);
		// 底部先给“材料条 + 按钮”留位，列表再吃剩下的高度。
		// 以前 listBottom 按固定 86 算，小窗口配方格直接被挤出屏幕。
		barH = shortScr == 1 ? 24 : 32;
		matY = btnY - (shortScr == 1 ? 30 : 40);
		listBottom = matY - (shortScr == 1 ? 14 : 18);
		int thirdBtn = pw / 3;

		addRenderableWidget(Button.builder(Component.literal("材料信息HUD:" + (PeiZhi.INS.hudKai ? "开" : "关")),
			b -> { PeiZhi.INS.hudKai = !PeiZhi.INS.hudKai; PeiZhi.INS.save();
				b.setMessage(Component.literal("材料信息HUD:" + (PeiZhi.INS.hudKai ? "开" : "关"))); })
			.bounds(px + 8, topY, thirdBtn - 12, btnH).build());
		addRenderableWidget(Button.builder(Component.literal("复制材料清单"), b -> copyMat())
			.bounds(px + thirdBtn + 4, topY, thirdBtn - 8, btnH).build());
		addRenderableWidget(Button.builder(Component.literal("辅助合成:" + (PeiZhi.INS.zhuGongKai ? "开" : "关")),
			b -> { PeiZhi.INS.zhuGongKai = !PeiZhi.INS.zhuGongKai; PeiZhi.INS.save();
				b.setMessage(Component.literal("辅助合成:" + (PeiZhi.INS.zhuGongKai ? "开" : "关"))); })
			.bounds(px + thirdBtn * 2, topY, thirdBtn - 12, btnH).build());

		searchLeft = new EditBox(Minecraft.getInstance().font, px + 8, searchY, third - 16, searchH, Component.literal("搜索材料"));
		searchRight = new EditBox(Minecraft.getInstance().font, px + third + 8, searchY, pw - third - 16, searchH, Component.literal("搜索全部物品 (右键清空)"));
		searchRight.setMaxLength(120);
		searchLeft.setMaxLength(120);
		addRenderableWidget(searchLeft);
		addRenderableWidget(searchRight);

		// 五个按钮均分整行，窄窗口也不会“数量+”被“添加”盖住
		int gap = 6;
		int bw = Math.max(42, (pw - 16 - gap * 4) / 5);
		addRenderableWidget(Button.builder(Component.literal("删除"), b -> removeSel())
			.bounds(px + 8, btnY, bw, btnH).build());
		addRenderableWidget(Button.builder(Component.literal("数量-"), b -> qtySel(-1))
			.bounds(px + 8 + (bw + gap), btnY, bw, btnH).build());
		addRenderableWidget(Button.builder(Component.literal("数量+"), b -> qtySel(1))
			.bounds(px + 8 + (bw + gap) * 2, btnY, bw, btnH).build());
		addRenderableWidget(Button.builder(Component.literal("添加"), b -> addSel())
			.bounds(px + 8 + (bw + gap) * 3, btnY, bw, btnH).build());
		addRenderableWidget(Button.builder(Component.literal("关闭"), b -> onClose())
			.bounds(px + 8 + (bw + gap) * 4, btnY, bw, btnH).build());
		// 目标清单是全局的（HUD 也要用）：重开材料板时把上次添加的恢复回来，
		// 否则按 N 关掉再打开，列表就空了，像“材料不见了”
		added.clear();
		for (QiuJie.Target t : ZhuangTai.INS.targets) added.add(new QiuJie.Target(t.ming, t.count));
		refreshViews();
		recalc();
	}

	private boolean thirdOpen() { return showRecipe || usesKey != null; }

	// 第三栏（配方/用途）单独关掉，不用把整个材料板关了
	private void closeThird() {
		showRecipe = false;
		usesKey = null;
		recipeHistory.clear();
		recipeTarget = null;
		lastRightQ = "\u0000";
		refreshViews();
	}

	// 搜索词变了才重算列表；数据变化用 leftDirty 强制重建左栏
	private void refreshViews() {
		String lq = searchLeft == null ? "" : searchLeft.getValue().trim().toLowerCase(Locale.ROOT);
		String rq = searchRight == null ? "" : searchRight.getValue().trim().toLowerCase(Locale.ROOT);
		boolean lChanged = !lq.equals(lastLeftQ);
		boolean rChanged = !rq.equals(lastRightQ);
		if (!lChanged && !rChanged && !leftDirty) return;
		if (lChanged) leftScroll = 0;
		if (rChanged) rightScroll = 0;
		lastLeftQ = lq; lastRightQ = rq;
		leftDirty = false;

		leftView = new ArrayList<>();
		LinkedHashMap<String, MatRow> rows = new LinkedHashMap<>();
		for (QiuJie.Target t : added) {
			MatRow r = new MatRow();
			r.key = t.ming; r.need = t.count; r.isTarget = true;
			rows.put(t.ming, r);
		}
		for (QiuJie.Bu bu : ZhuangTai.INS.latest.crafts.values()) {
			MatRow r = rows.get(bu.ming);
			if (r == null) { r = new MatRow(); r.key = bu.ming; rows.put(bu.ming, r); }
			r.need = bu.need; r.isCraft = true;
		}
		for (QiuJie.Cai c : ZhuangTai.INS.latest.base.values()) {
			MatRow r = rows.get(c.key);
			if (r == null) { r = new MatRow(); r.key = c.key; rows.put(c.key, r); }
			if (!r.isCraft) r.need = c.count;
		}
		for (MatRow r : rows.values()) {
			r.disp = TuBiao.mingOf(r.key);
			WuPin wp = ShuJuKu.INS.get(r.key);
			r.craftable = wp != null && wp.keHeCheng();
			String low = r.disp.toLowerCase(Locale.ROOT);
			if (lq.isEmpty() || low.contains(lq) || r.key.toLowerCase(Locale.ROOT).contains(lq)) leftView.add(r);
		}

		if (usesKey != null) {
			rightView = ShuJuKu.INS.getUses(usesKey);
		} else {
			rightView = ShuJuKu.INS.sou(rq);
		}
	}

	// 用途视图要在右栏顶端留一行放标题，其余情况列表从 listTop 开始
	private int rightHead() { return usesKey != null ? 18 : 0; }
	private int leftRows() { return Math.max(1, (listBottom - listTop - 2) / ROW); }
	private int rightRows() { return Math.max(1, (listBottom - listTop - 2 - rightHead()) / ROW); }
	// 配方区宽度自适应：窗口很窄时给右栏留至少 92px，避免 rightListW 变负导致布局翻转。
	private int recipeW() { return Math.min(RECIPE_W, Math.max(150, (int) (pw * 0.36))); }
	private int rightListW() {
		if (!thirdOpen()) return pw - third - 12;
		// 第三栏打开后中间栏只剩一条缝时干脆不画，免得和配方/用途面板叠在一起
		int gap = recipeX() - (px + third + 6) - 6;
		return gap >= 60 ? gap : 0;
	}
	private int recipeX() { return px + pw - recipeW() - 4; }
	// 底部材料条宽度：配方视图时让到配方面板左边，别叠在一起
	private int baseBarW() { return thirdOpen() ? recipeX() - px - 14 : pw - 16; }

	private void clampScroll() {
		leftScroll = Math.max(0, Math.min(leftScroll, Math.max(0, leftView.size() - leftRows())));
		rightScroll = Math.max(0, Math.min(rightScroll, Math.max(0, rightView.size() - rightRows())));
		int cols = Math.max(1, baseBarW() / 34);
		matScroll = Math.max(0, Math.min(matScroll, Math.max(0, curBase.size() - cols)));
	}

	void recalc() {
		List<QiuJie.Target> ts = new ArrayList<>();
		for (QiuJie.Target t : added) ts.add(new QiuJie.Target(t.ming, t.count));
		// setTargets 内部会 recalc() -> QiuJie.run(targets)，求解结果存在 INS.latest。
		// 之前这里又单独跑一遍 QiuJie.run()，同一份目标展开两遍，纯属浪费。
		ZhuangTai.INS.setTargets(ts);
		curBase = new ArrayList<>(ZhuangTai.INS.latest.base.values());
		leftDirty = true;
		refreshViews();
		clampScroll();
	}

	void addSel() {
		if (selRight == null) return;
		boolean found = false;
		for (QiuJie.Target t : added) if (t.ming.equals(selRight.ming)) { t.count++; found = true; break; }
		if (!found) added.add(new QiuJie.Target(selRight.ming, 1));
		selLeft = selRight;
		selLeftKey = selRight.ming;
		openRecipe(selRight, false);
		lastLeftQ = "\u0000";
		recalc();
	}

	void removeSel() {
		if (selLeftKey == null) return;
		added.removeIf(t -> t.ming.equals(selLeftKey));
		selLeft = null; selLeftKey = null; showRecipe = false; recipeHistory.clear();
		lastLeftQ = "\u0000";
		recalc();
	}

	void qtySel(int d) {
		if (selLeftKey == null) return;
		for (QiuJie.Target t : added) if (t.ming.equals(selLeftKey)) { t.count = Math.max(1, t.count + d); break; }
		recalc();
	}

	void openRecipe(WuPin wp, boolean fromLeft) {
		if (wp == null || !wp.keHeCheng()) { showRecipe = false; return; }
		// 配方视图和用途视图互斥，同时开着右栏会叠成一团
		usesKey = null;
		showRecipe = true; recipeFromLeft = fromLeft;
		recipeTarget = wp; recipeHistory.clear(); recipePage = 0;
		lastRightQ = "\u0000";
		refreshViews();
	}

	void openUses(String key) {
		if (key == null) return;
		showRecipe = false; recipeHistory.clear();
		usesKey = key;
		lastRightQ = "\u0000";
		refreshViews();
	}

	void copyMat() {
		StringBuilder sb = new StringBuilder();
		for (QiuJie.Cai c : curBase) sb.append(c.key).append(" x").append(c.count).append("\n");
		com.dnteam.linchi.JianRong.copy(sb.toString());
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
		lastMx = mx; lastMy = my;
		refreshViews();
		clampScroll();
		// 不要调用 extractBackground()：父类流程已调用，重复会触发 "Can only blur once per frame" 崩溃
		GuiTool.panel(g, px, py, pw, ph, 0xF0101014);
		GuiTool.border(g, px, py, pw, ph, 0xFF7B6EF6);

		GuiTool.modIcon(g, px + 8, titleY - 2, shortScr == 1 ? 12 : 14);
		GuiTool.text(g, "LinsMagic 材料板", px + (shortScr == 1 ? 24 : 26), titleY, 0xFFFFD54F);
		GuiTool.text(g, "N / ESC 关闭", px + pw - 96, titleY, 0xFF888888);

		drawLeft(g, mx, my);
		drawRight(g, mx, my);
		if (showRecipe) drawRecipe(g, mx, my);
		drawBaseBar(g, mx, my);
		super.extractRenderState(g, mx, my, delta);
		drawTip(g, mx, my);
	}

	private void drawLeft(GuiGraphicsExtractor g, int mx, int my) {
		int w = third - 12;
		GuiTool.panel(g, px + 6, listTop, w, listBottom - listTop, 0x40181828);
		int shown = 0;
		for (int i = leftScroll; i < leftView.size() && shown < leftRows(); i++, shown++) {
			MatRow r = leftView.get(i);
			int y = listTop + 2 + shown * ROW;
			boolean sel = r.key.equals(selLeftKey);
			boolean hov = GuiTool.hovered(mx, my, px + 7, y - 2, w - 2, ROW);
			if (sel || hov) GuiTool.panel(g, px + 7, y - 2, w - 2, ROW, sel ? 0x50606099 : 0x28384070);
			GuiTool.item(g, r.key, px + 10, y, 12);
			int color = sel ? 0xFFFFFFFF : (r.isTarget ? 0xFFFFD54F : (r.isCraft ? 0xFF9AD0FF : 0xFFDDDDDD));
			int rightPad = r.craftable ? TOGGLE_W : 0;
			GuiTool.text(g, cut(r.disp + " x" + r.need, Math.max(24, w - 30 - rightPad)), px + 26, y + 3, color);
			if (r.craftable) {
				String act = r.isCraft ? "收起" : "展开";
				int tw = Minecraft.getInstance().font.width(act);
				GuiTool.text(g, act, px + 6 + w - 6 - tw, y + 3, r.isCraft ? 0xFF7FE0FF : 0xFFFFD54F);
			}
		}
		if (leftView.size() > leftRows()) drawScrollBar(g, px + 6 + w - 4, listTop, listBottom - listTop, leftScroll, leftView.size(), leftRows());
	}

	private void drawRight(GuiGraphicsExtractor g, int mx, int my) {
		int w = rightListW();
		GuiTool.panel(g, px + third + 6, listTop, w, listBottom - listTop, 0x40182818);
		if (usesKey != null) {
			GuiTool.text(g, cut("用途: " + usesKey, w - 24), px + third + 10, listTop + 3, 0xFF7FE0FF);
			GuiTool.cross(g, px + third + 6 + w - 18, listTop + 2, usesCloseHit(mx, my));
		}
		int shown = 0;
		for (int i = rightScroll; i < rightView.size() && shown < rightRows(); i++, shown++) {
			WuPin wp = rightView.get(i);
			int y = listTop + 2 + rightHead() + shown * ROW;
			boolean sel = selRight != null && selRight.ming.equals(wp.ming);
			boolean hov = GuiTool.hovered(mx, my, px + third + 7, y - 2, w - 2, ROW);
			if (sel || hov) GuiTool.panel(g, px + third + 7, y - 2, w - 2, ROW, sel ? 0x50609960 : 0x28387050);
			GuiTool.item(g, wp.ming, px + third + 10, y, 12);
			GuiTool.text(g, wp.ming, px + third + 26, y + 3, sel ? 0xFFFFFFFF : 0xFFDDDDDD);
		}
		if (rightView.size() > rightRows()) drawScrollBar(g, px + third + 6 + w - 4, listTop, listBottom - listTop, rightScroll, rightView.size(), rightRows());
	}

	private boolean usesCloseHit(double mx, double my) {
		return usesKey != null && GuiTool.hovered(mx, my, px + third + 6 + rightListW() - 20, listTop + 2, 16, 16);
	}

	private boolean recipeCloseHit(double mx, double my) {
		return showRecipe && GuiTool.hovered(mx, my, recipeX() + recipeW() - 20, listTop + 2, 16, 16);
	}

	// 第三栏窄的时候标题会顶到关闭钮上，截一下
	private String cut(String s, int maxW) {
		net.minecraft.client.gui.Font f = Minecraft.getInstance().font;
		if (f.width(s) <= maxW) return s;
		StringBuilder b = new StringBuilder();
		for (char c : s.toCharArray()) {
			if (f.width(b.toString() + c + "..") > maxW) break;
			b.append(c);
		}
		return b + "..";
	}

	private boolean isDouble(long now, long last, String key, String lastKey) {
		if (now < last) return false;
		return key != null && key.equals(lastKey) && now - last <= DBL_MS;
	}

	private void drawScrollBar(GuiGraphicsExtractor g, int x, int y, int h, int off, int total, int rows) {
		int bar = Math.max(12, h * rows / total);
		int maxOff = Math.max(1, total - rows);
		int yy = y + (h - bar) * off / maxOff;
		g.fill(x, yy, x + 3, yy + bar, 0xFF8C7BF6);
	}

	// 9 格摊平结果按配方对象缓存：jiuGe 每帧跑一遍纯属浪费
	private PeiFang gridPf;
	private List<String> gridCache;
	private List<String> gridOf(PeiFang pf) {
		if (gridPf != pf) { gridPf = pf; gridCache = pf.jiuGe(); }
		return gridCache;
	}

	private void drawRecipe(GuiGraphicsExtractor g, int mx, int my) {
		WuPin wp = recipeTarget;
		if (wp == null) return;
		int rx = recipeX(), rw = recipeW(), rh = listBottom - listTop;
		GuiTool.panel(g, rx, listTop, rw, rh, 0xF0181820);
		GuiTool.border(g, rx, listTop, rw, rh, 0xFFF0A500);
		GuiTool.text(g, cut(wp.ming, rw - 34), rx + 8, listTop + 5, 0xFFFFD54F);
		GuiTool.cross(g, rx + rw - 20, listTop + 2, recipeCloseHit(mx, my));
		if (!recipeHistory.isEmpty()) GuiTool.text(g, "<-", rx + rw - 38, listTop + 5, 0xFF7FE0FF);

		List<PeiFang> pfs = recipesOf(wp);
		if (pfs.isEmpty()) return;
		recipePage = Math.max(0, Math.min(recipePage, pfs.size() - 1));
		PeiFang pf = pfs.get(recipePage);
		// 头部高度跟着面板走。Modern UI 下逻辑高度可能只有一百多像素，
		// 固定 38px 的头会把 3×3 挤没，之前直接显示“窗口太矮”。
		int headH = Math.max(14, Math.min(38, rh / 5));
		if (headH >= 30) {
			GuiTool.text(g, (pf.mirror ? "魔镜" : "工作台") + (pfs.size() > 1 ? " (" + (recipePage + 1) + "/" + pfs.size() + ")" : "") + "  产出x" + pf.count,
				rx + 8, listTop + 21, 0xFFAAAAAA);
		}
		int availH = rh - headH - 10;
		int cell = Math.min(40, Math.max(10, availH / 3 - 3));
		int span = cell + 3;
		if (cell < 10 || headH + span * 3 > rh - 2) {
			gridCell = 0;
			GuiTool.text(g, "窗口太矮，拉大一点再看配方", rx + 8, listTop + headH, 0xFF888888);
			return;
		}
		int gw = cell * 3 + 6;
		gridCell = cell;
		gridX0 = rx + (rw - gw) / 2;
		gridY0 = listTop + headH;
		List<String> grid = gridOf(pf);
		int icon = Math.max(8, Math.min(12, cell - 2));
		for (int i = 0; i < 9; i++) {
			int gx = gridX0 + (i % 3) * span;
			int gy = gridY0 + (i / 3) * span;
			String k = grid.get(i);
			boolean hov = k != null && GuiTool.hovered(mx, my, gx, gy, cell, cell);
			GuiTool.panel(g, gx, gy, cell, cell, hov ? 0x70504580 : 0x50303040);
			GuiTool.border(g, gx, gy, cell, cell, hov ? 0xFFB9A6FF : 0xFF605070);
			if (k != null) GuiTool.item(g, k, gx + (cell - icon) / 2, gy + (cell - icon) / 2, icon);
		}
		int hintY = gridY0 + span * 3 + 2;
		if (hintY + 9 <= listBottom) {
			GuiTool.text(g, "点材料可展开配方", rx + 8, hintY, 0xFF888888);
		}
	}

	private void drawBaseBar(GuiGraphicsExtractor g, int mx, int my) {
		int bw = baseBarW();
		GuiTool.text(g, "基础材料 (" + curBase.size() + ")  滚轮翻动", px + 8, matY - 11, 0xFF7FE0FF);
		GuiTool.panel(g, px + 6, matY, bw, barH, 0x50101018);
		int cols = Math.max(1, bw / 34);
		int start = Math.min(matScroll, Math.max(0, curBase.size() - cols));
		for (int i = 0; i < cols && start + i < curBase.size(); i++) {
			QiuJie.Cai c = curBase.get(start + i);
			int cx = px + 10 + i * 34;
			boolean hov = GuiTool.hovered(mx, my, cx, matY, 30, barH);
			if (hov) GuiTool.panel(g, cx, matY, 30, barH, 0x30406080);
			GuiTool.item(g, c.key, cx + 1, matY + 1, 12);
			GuiTool.text(g, String.valueOf(c.count), cx + 1, matY + (shortScr == 1 ? 13 : 17), 0xFFFFFFFF);
		}
	}

	// tooltip 交给原版那套，Jade/REI 之类的挂在上面的模组也照样能出提示
	private void drawTip(GuiGraphicsExtractor g, int mx, int my) {
		if (my < listTop || my >= listBottom) return;
		// 配方面板里的 3×3 也要有名字提示
		if (showRecipe && gridCell > 0) {
			int span = gridCell + 3;
			int gw = span * 3;
			if (mx >= gridX0 && mx < gridX0 + gw && my >= gridY0 && my < gridY0 + gw) {
				int col = (mx - gridX0) / span;
				int row = (my - gridY0) / span;
				int gx = gridX0 + col * span;
				int gy = gridY0 + row * span;
				if (col >= 0 && col < 3 && row >= 0 && row < 3 && mx < gx + gridCell && my < gy + gridCell) {
					PeiFang pf = curPf();
					if (pf != null) {
						List<String> grid = gridOf(pf);
						String k = grid.get(row * 3 + col);
						if (k != null) GuiTool.itemTip(g, k, mx, my);
					}
				}
				return;
			}
		}
		if (mx >= px + 6 && mx < px + third - 6) {
			int row = (my - listTop - 2) / ROW + leftScroll;
			if (row >= 0 && row < leftView.size()) {
				GuiTool.itemTip(g, leftView.get(row).key, mx, my);
			}
		} else if (mx >= px + third + 6 && mx < px + third + 6 + rightListW()) {
			int row = (my - listTop - 2 - rightHead()) / ROW + rightScroll;
			if (row >= 0 && row < rightView.size()) {
				GuiTool.itemTip(g, rightView.get(row).ming, mx, my);
			}
		}
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
		int cx = (int) e.x(), cy = (int) e.y();
		int button = e.button();
		// 点到搜索框：先显式把容器焦点交过去，再走原版分发。
		// 有输入法/别的模组改动焦点链时，不主动这一步可能点了也 focus 不上（表现：光标在框里却打不了字）
		if (GuiTool.hovered(cx, cy, px + 8, searchY, third - 16, searchH)) this.setFocused(searchLeft);
		else if (GuiTool.hovered(cx, cy, px + third + 8, searchY, pw - third - 16, searchH)) this.setFocused(searchRight);
		// 右键搜索框直接清空，JEI 就是这个习惯
		if (button == 1 && cy >= searchY && cy < searchY + searchH) {
			if (cx >= px + 8 && cx < px + third - 8) { searchLeft.setValue(""); return true; }
			if (cx >= px + third + 8 && cx < px + pw - 8) {
				if (usesKey != null) { closeThird(); }
				else searchRight.setValue("");
				return true;
			}
		}
		// 第三栏自己的关闭钮
		if (recipeCloseHit(cx, cy) || usesCloseHit(cx, cy)) { closeThird(); return true; }

		if (cy >= listTop && cy < listBottom) {
			int rowL = (cy - listTop - 2) / ROW + leftScroll;
			int rowR = (cy - listTop - 2 - rightHead()) / ROW + rightScroll;
			if (cx >= px + 6 && cx < px + third - 6 && rowL >= 0 && rowL < leftView.size()) {
				MatRow r = leftView.get(rowL);
				if (button == 1) {
					selLeftKey = r.key; selLeft = ShuJuKu.INS.get(r.key);
					openThirdUses(r.key);
					return true;
				}
				long now = System.currentTimeMillis();
				// 左栏展开/收起会让行换位置，双击按“同一个纵坐标”认，不按 key
				boolean dbl = now >= lastLeftAt && lastLeftKey != null
					&& now - lastLeftAt <= DBL_MS && Math.abs(cy - lastLeftY) <= 8;
				if (dbl) {
					// 第一下已经展开/收起了，双击时先还原再开配方
					WuPin prev = ShuJuKu.INS.get(lastLeftKey);
					if (prev != null && prev.keHeCheng()) toggleNode(lastLeftKey);
					selLeftKey = lastLeftKey;
					selLeft = prev;
					openThirdRecipe(prev, true);
					lastLeftAt = 0;
					return true;
				}
				selLeftKey = r.key;
				selLeft = ShuJuKu.INS.get(r.key);
				lastLeftAt = now; lastLeftKey = r.key; lastLeftY = cy;
				// 整行都能点，单击就展开/收起；不可合成的只选中
				if (r.craftable) toggleNode(r.key);
				return true;
			}
			if (cx >= px + third + 6 && cx < px + third + 6 + rightListW() && rowR >= 0 && rowR < rightView.size()) {
				WuPin wp = rightView.get(rowR);
				if (button == 1) {
					selRight = wp;
					openThirdUses(wp.ming);
					return true;
				}
				long now = System.currentTimeMillis();
				boolean dbl = isDouble(now, lastRightAt, wp.ming, lastRightKey);
				selRight = wp;
				if (dbl) {
					openThirdRecipe(wp, false);
					lastRightAt = 0;
					return true;
				}
				lastRightAt = now; lastRightKey = wp.ming;
				// 单击只选中；双击才开配方
				return true;
			}
			// 配方面板里的材料：能展开就往里走一层
			if (showRecipe && cx >= recipeX()) {
				PeiFang pf = curPf();
				if (pf != null) {
					List<String> grid = gridOf(pf);
					int span = gridCell + 3;
					for (int i = 0; i < 9; i++) {
						int gx = gridX0 + (i % 3) * span;
						int gy = gridY0 + (i / 3) * span;
						String k = grid.get(i);
						if (k != null && GuiTool.hovered(cx, cy, gx, gy, gridCell, gridCell)) {
							WuPin next = ShuJuKu.INS.get(k);
							if (next != null && next.keHeCheng() && next != recipeTarget) {
								recipeHistory.push(recipeTarget);
								recipeTarget = next; recipePage = 0;
							} else {
								openThirdUses(k);
							}
							return true;
						}
					}
				}
				if (cx >= recipeX() + recipeW() - 40 && cy < listTop + 24 && !recipeHistory.isEmpty()) {
					recipeTarget = recipeHistory.pop(); recipePage = 0; return true;
				}
			}
		}
		// 提示里一直写着“右键空白返回”，但以前只有搜索框那块吃这个手势。
		// 现在空白处右键真能关掉第三栏，不用整个面板关掉再开。
		if (button == 1 && thirdOpen()) { closeThird(); return true; }
		return super.mouseClicked(e, doubleClick);
	}

	private void openThirdRecipe(WuPin wp, boolean fromLeft) {
		if (wp == null) return;
		if (wp.keHeCheng()) openRecipe(wp, fromLeft);
		else openThirdUses(wp.ming);
	}

	// 没用途的物品别把右栏切成一张空白“用途”页
	private void openThirdUses(String key) {
		if (key == null) return;
		if (ShuJuKu.INS.getUses(key).isEmpty()) return;
		openUses(key);
	}

	// 点“展开/收起”：决定这个东西继续拆配方，还是当成材料去收集
	private void toggleNode(String key) {
		if (ZhuangTai.INS.zhanKai.contains(key)) {
			ZhuangTai.INS.zhanKai.remove(key);
			ZhuangTai.INS.shouQi.add(key);
		} else if (ZhuangTai.INS.shouQi.contains(key)) {
			ZhuangTai.INS.shouQi.remove(key);
			ZhuangTai.INS.zhanKai.add(key);
		} else if (ZhuangTai.INS.latest.crafts.containsKey(key)) {
			ZhuangTai.INS.shouQi.add(key);
		} else {
			ZhuangTai.INS.zhanKai.add(key);
		}
		// 重算后 HUD 的材料/步骤缓存一起刷新
		ZhuangTai.INS.recalc();
		curBase = new ArrayList<>(ZhuangTai.INS.latest.base.values());
		leftDirty = true;
		refreshViews();
		clampScroll();
	}

	// 当前目标的配方列表（工作台/魔镜）：按目标缓存，避免每帧 new ArrayList
	private WuPin pfsTarget;
	private List<PeiFang> pfsCache;
	private List<PeiFang> recipesOf(WuPin wp) {
		if (wp == null) return Collections.emptyList();
		if (wp == pfsTarget && pfsCache != null) return pfsCache;
		List<PeiFang> pfs = new ArrayList<>(2);
		if (wp.gongFang != null) pfs.add(wp.gongFang);
		if (wp.moJing != null) pfs.add(wp.moJing);
		pfsTarget = wp; pfsCache = pfs;
		return pfs;
	}

	private PeiFang curPf() {
		List<PeiFang> pfs = recipesOf(recipeTarget);
		return pfs.isEmpty() ? null : pfs.get(Math.max(0, Math.min(recipePage, pfs.size() - 1)));
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double dx, double dy) {
		int dir = dy > 0 ? -1 : 1;
		if (my >= matY && my < matY + barH && mx >= px + 6 && mx < px + 6 + baseBarW()) {
			matScroll += dir;
		} else if (showRecipe && mx >= recipeX() && mx < recipeX() + recipeW() && my >= listTop && my < listBottom) {
			recipePage += dir; // 多个配方之间来回切
		} else if (my >= listTop && my < listBottom && mx >= px + 6 && mx < px + third - 6) {
			leftScroll += dir;
		} else if (my >= listTop && my < listBottom && mx >= px + third + 6 && mx < px + third + 6 + rightListW()) {
			rightScroll += dir;
		} else return false;
		clampScroll();
		return true;
	}

	// 正在打字的搜索框（容器焦点可能和框自身焦点不一致，以框自身为准）
	private EditBox focusedBox() {
		if (searchLeft != null && searchLeft.isFocused()) return searchLeft;
		if (searchRight != null && searchRight.isFocused()) return searchRight;
		return null;
	}

	@Override
	public boolean charTyped(net.minecraft.client.input.CharacterEvent e) {
		// 显式转发给搜索框：个别版本/输入法组合下，原版容器分发到不了框
		EditBox f = focusedBox();
		if (f != null) return f.charTyped(e);
		return super.charTyped(e);
	}

	@Override
	public boolean preeditUpdated(net.minecraft.client.input.PreeditEvent e) {
		// 中文输入法组字过程也要转给框，否则候选词上不去
		EditBox f = focusedBox();
		if (f != null) return f.preeditUpdated(e);
		return super.preeditUpdated(e);
	}

	@Override
	public boolean keyPressed(KeyEvent e) {
		// 搜索框聚焦时先把按键交给框（Backspace 删字、方向键移动光标等）。
		// 框不处理的键再走我们的全局手势，避免“光标在框里却打不了字”
		EditBox f = focusedBox();
		if (f != null && f.keyPressed(e)) return true;
		// 全部用 InputConstants 常量：26.3 的键码和 26.1/26.2 完全不同。
		// 硬编码数字会让 ESC/N/Ctrl+F/Backspace/翻页手势在 26.3 上失效。
		// ESC 关闭。N 的关闭交给 tick 里的 toggle 统一处理，避免关完同帧被重开。
		if (e.key() == InputConstants.KEY_ESCAPE) { onClose(); return true; }
		if (e.hasControlDown() && e.key() == InputConstants.KEY_F) { setFocused(searchRight); return true; }
		if (e.key() == InputConstants.KEY_BACKSPACE) { // Backspace 往回退
			if (showRecipe && !recipeHistory.isEmpty()) { recipeTarget = recipeHistory.pop(); recipePage = 0; return true; }
			if (thirdOpen()) { closeThird(); return true; }
		}
		if (!isTyping()) {
			if (e.key() == InputConstants.KEY_HOME) { if (inLeft()) leftScroll = 0; else rightScroll = 0; return true; }
			if (e.key() == InputConstants.KEY_END) { if (inLeft()) leftScroll = Integer.MAX_VALUE; else rightScroll = Integer.MAX_VALUE; clampScroll(); return true; }
			if (e.key() == InputConstants.KEY_PAGEUP) { if (inLeft()) leftScroll -= 5; else rightScroll -= 5; clampScroll(); return true; }
			if (e.key() == InputConstants.KEY_PAGEDOWN) { if (inLeft()) leftScroll += 5; else rightScroll += 5; clampScroll(); return true; }
		}
		return super.keyPressed(e);
	}

	private boolean inLeft() { return lastMx >= px + 6 && lastMx < px + third - 6; }
	boolean isTyping() {
		return (searchLeft != null && searchLeft.isFocused()) || (searchRight != null && searchRight.isFocused());
	}

	@Override
	public void onClose() {
		GuiTool.setOpen(false);
		PeiZhi.INS.save();
		Minecraft.getInstance().setScreenAndShow(parent);
	}
}
