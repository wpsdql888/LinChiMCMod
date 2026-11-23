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
	private static final int ROW = 22;
	private static final int RECIPE_W = 200;

	private final List<QiuJie.Target> added = new ArrayList<>();
	private WuPin selLeft, selRight;

	// 搜索和滚动，这四样是分开的，互不串台
	private EditBox searchLeft, searchRight;
	private String lastLeftQ = "", lastRightQ = "\u0000";
	private List<QiuJie.Target> leftView = new ArrayList<>();
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
		super(Component.literal("LinChi 材料板"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		pw = Math.min(980, this.width - 32);
		ph = Math.min(580, this.height - 48);
		px = (this.width - pw) / 2;
		py = (this.height - ph) / 2;
		third = pw / 3;
		listTop = py + 76;
		listBottom = py + ph - 86;
		matY = py + ph - 76;
		btnY = py + ph - 28;
		int thirdBtn = pw / 3;

		addRenderableWidget(Button.builder(Component.literal("材料信息HUD:" + (PeiZhi.INS.hudKai ? "开" : "关")),
			b -> { PeiZhi.INS.hudKai = !PeiZhi.INS.hudKai; PeiZhi.INS.save();
				b.setMessage(Component.literal("材料信息HUD:" + (PeiZhi.INS.hudKai ? "开" : "关"))); })
			.bounds(px + 8, py + 8, thirdBtn - 12, 20).build());
		addRenderableWidget(Button.builder(Component.literal("复制材料清单"), b -> copyMat())
			.bounds(px + thirdBtn + 4, py + 8, thirdBtn - 8, 20).build());
		addRenderableWidget(Button.builder(Component.literal("辅助合成:" + (PeiZhi.INS.zhuGongKai ? "开" : "关")),
			b -> { PeiZhi.INS.zhuGongKai = !PeiZhi.INS.zhuGongKai; PeiZhi.INS.save();
				b.setMessage(Component.literal("辅助合成:" + (PeiZhi.INS.zhuGongKai ? "开" : "关"))); })
			.bounds(px + thirdBtn * 2, py + 8, thirdBtn - 12, 20).build());

		searchLeft = new EditBox(Minecraft.getInstance().font, px + 8, py + 50, third - 16, 20, Component.literal("搜索已添加"));
		searchRight = new EditBox(Minecraft.getInstance().font, px + third + 8, py + 50, third * 2 - 16, 20, Component.literal("搜索全部物品 (右键清空)"));
		searchRight.setMaxLength(120);
		searchLeft.setMaxLength(120);
		addRenderableWidget(searchLeft);
		addRenderableWidget(searchRight);

		addRenderableWidget(Button.builder(Component.literal("删除"), b -> removeSel()).bounds(px + 8, btnY, 66, 20).build());
		addRenderableWidget(Button.builder(Component.literal("数量-"), b -> qtySel(-1)).bounds(px + 78, btnY, 50, 20).build());
		addRenderableWidget(Button.builder(Component.literal("数量+"), b -> qtySel(1)).bounds(px + 132, btnY, 50, 20).build());
		addRenderableWidget(Button.builder(Component.literal("添加"), b -> addSel()).bounds(px + third + 8, btnY, 66, 20).build());
		addRenderableWidget(Button.builder(Component.literal("关闭"), b -> onClose())
			.bounds(px + pw - 78, btnY, 70, 20).build());
		// 目标清单是全局的（HUD 也要用）：重开材料板时把上次添加的恢复回来，
		// 否则按 N 关掉再打开，列表就空了，像“材料不见了”
		added.clear();
		for (QiuJie.Target t : ZhuangTai.INS.targets) added.add(new QiuJie.Target(t.ming, t.count));
		refreshViews();
		recalc();
	}

	// 搜索词变了才重算列表，别每帧扫 2160 条
	private void refreshViews() {
		String lq = searchLeft == null ? "" : searchLeft.getValue().trim().toLowerCase(Locale.ROOT);
		String rq = searchRight == null ? "" : searchRight.getValue().trim().toLowerCase(Locale.ROOT);
		if (lq.equals(lastLeftQ) && rq.equals(lastRightQ)) return;
		lastLeftQ = lq; lastRightQ = rq;

		leftView = new ArrayList<>();
		for (QiuJie.Target t : added) {
			WuPin wp = ShuJuKu.INS.get(t.ming);
			if (lq.isEmpty() || (wp != null && wp.mingLower.contains(lq))) leftView.add(t);
		}

		if (usesKey != null) {
			rightView = ShuJuKu.INS.getUses(usesKey);
		} else {
			rightView = ShuJuKu.INS.sou(rq);
		}
		leftScroll = 0; rightScroll = 0;
	}

	// 用途视图要在右栏顶端留一行放标题，其余情况列表从 listTop 开始
	private int rightHead() { return usesKey != null ? 18 : 0; }
	private int leftRows() { return Math.max(1, (listBottom - listTop - 2) / ROW); }
	private int rightRows() { return Math.max(1, (listBottom - listTop - 2 - rightHead()) / ROW); }
	private int rightListW() { return showRecipe ? third * 2 - RECIPE_W - 24 : third * 2 - 12; }
	private int recipeX() { return px + third + 8 + rightListW() + 8; }
	// 底部材料条宽度：配方视图时让到配方面板左边，别叠在一起
	private int baseBarW() { return showRecipe ? recipeX() - px - 20 : pw - 16; }

	private void clampScroll() {
		leftScroll = Math.max(0, Math.min(leftScroll, Math.max(0, leftView.size() - leftRows())));
		rightScroll = Math.max(0, Math.min(rightScroll, Math.max(0, rightView.size() - rightRows())));
		int cols = Math.max(1, baseBarW() / 42);
		matScroll = Math.max(0, Math.min(matScroll, Math.max(0, curBase.size() - cols)));
	}

	void recalc() {
		List<QiuJie.Target> ts = new ArrayList<>();
		for (QiuJie.Target t : added) ts.add(new QiuJie.Target(t.ming, t.count));
		QiuJie q = QiuJie.run(ts);
		curBase = new ArrayList<>(q.base.values());
		ZhuangTai.INS.setTargets(ts);
		clampScroll();
	}

	void addSel() {
		if (selRight == null) return;
		boolean found = false;
		for (QiuJie.Target t : added) if (t.ming.equals(selRight.ming)) { t.count++; found = true; break; }
		if (!found) added.add(new QiuJie.Target(selRight.ming, 1));
		selLeft = selRight;
		openRecipe(selRight, false);
		lastLeftQ = "\u0000";
		refreshViews();
		recalc();
	}

	void removeSel() {
		if (selLeft == null) return;
		added.removeIf(t -> t.ming.equals(selLeft.ming));
		selLeft = null; showRecipe = false; recipeHistory.clear();
		lastLeftQ = "\u0000";
		refreshViews(); recalc();
	}

	void qtySel(int d) {
		if (selLeft == null) return;
		for (QiuJie.Target t : added) if (t.ming.equals(selLeft.ming)) { t.count = Math.max(1, t.count + d); break; }
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

		GuiTool.modIcon(g, px + 8, py + 30, 16);
		GuiTool.text(g, "LinChi 材料板", px + 28, py + 32, 0xFFFFD54F);
		GuiTool.text(g, "N / ESC 关闭", px + pw - 96, py + 32, 0xFF888888);

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
			QiuJie.Target t = leftView.get(i);
			int y = listTop + 2 + shown * ROW;
			WuPin wp = ShuJuKu.INS.get(t.ming);
			boolean sel = selLeft != null && selLeft.ming.equals(t.ming);
			boolean hov = GuiTool.hovered(mx, my, px + 7, y - 2, w - 2, ROW);
			if (sel || hov) GuiTool.panel(g, px + 7, y - 2, w - 2, ROW, sel ? 0x50606099 : 0x28384070);
			if (wp != null) GuiTool.item(g, wp.base, px + 10, y);
			GuiTool.text(g, t.ming + " x" + t.count, px + 32, y + 3, sel ? 0xFFFFFFFF : 0xFFDDDDDD);
		}
		if (leftView.size() > leftRows()) drawScrollBar(g, px + 6 + w - 4, listTop, listBottom - listTop, leftScroll, leftView.size(), leftRows());
	}

	private void drawRight(GuiGraphicsExtractor g, int mx, int my) {
		int w = rightListW();
		GuiTool.panel(g, px + third + 6, listTop, w, listBottom - listTop, 0x40182818);
		if (usesKey != null) {
			GuiTool.text(g, "用途: " + usesKey + "  (右键空白返回)", px + third + 10, listTop + 2, 0xFF7FE0FF);
		}
		int shown = 0;
		for (int i = rightScroll; i < rightView.size() && shown < rightRows(); i++, shown++) {
			WuPin wp = rightView.get(i);
			int y = listTop + 2 + rightHead() + shown * ROW;
			boolean sel = selRight != null && selRight.ming.equals(wp.ming);
			boolean hov = GuiTool.hovered(mx, my, px + third + 7, y - 2, w - 2, ROW);
			if (sel || hov) GuiTool.panel(g, px + third + 7, y - 2, w - 2, ROW, sel ? 0x50609960 : 0x28387050);
			GuiTool.item(g, wp.base, px + third + 10, y);
			GuiTool.text(g, wp.ming, px + third + 32, y + 3, sel ? 0xFFFFFFFF : 0xFFDDDDDD);
		}
		if (rightView.size() > rightRows()) drawScrollBar(g, px + third + 6 + w - 4, listTop, listBottom - listTop, rightScroll, rightView.size(), rightRows());
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
		int rx = recipeX(), rw = RECIPE_W, rh = listBottom - listTop;
		GuiTool.panel(g, rx, listTop, rw, rh, 0xF0181820);
		GuiTool.border(g, rx, listTop, rw, rh, 0xFFF0A500);
		GuiTool.item(g, wp.base, rx + 8, listTop + 8);
		GuiTool.text(g, wp.ming, rx + 30, listTop + 10, 0xFFFFD54F);
		if (!recipeHistory.isEmpty()) GuiTool.text(g, "<-", rx + rw - 30, listTop + 10, 0xFF7FE0FF);

		List<PeiFang> pfs = new ArrayList<>();
		if (wp.gongFang != null) pfs.add(wp.gongFang);
		if (wp.moJing != null) pfs.add(wp.moJing);
		if (pfs.isEmpty()) return;
		recipePage = Math.max(0, Math.min(recipePage, pfs.size() - 1));
		PeiFang pf = pfs.get(recipePage);
		GuiTool.text(g, (pf.mirror ? "魔镜" : "工作台") + (pfs.size() > 1 ? "  (" + (recipePage + 1) + "/" + pfs.size() + ") 滚轮切换" : "") + "  产出x" + pf.count,
			rx + 8, listTop + 32, 0xFFAAAAAA);

		List<String> grid = gridOf(pf);
		for (int i = 0; i < 9; i++) {
			int gx = rx + 22 + (i % 3) * 46, gy = listTop + 48 + (i / 3) * 46;
			String k = grid.get(i);
			boolean hov = k != null && GuiTool.hovered(mx, my, gx, gy, 40, 40);
			GuiTool.panel(g, gx, gy, 40, 40, hov ? 0x70504580 : 0x50303040);
			GuiTool.border(g, gx, gy, 40, 40, hov ? 0xFFB9A6FF : 0xFF605070);
			if (k != null) GuiTool.item(g, k, gx + 12, gy + 12);
		}
		GuiTool.text(g, "点材料可展开配方", rx + 8, listTop + 48 + 3 * 46 + 4, 0xFF888888);
	}

	private void drawBaseBar(GuiGraphicsExtractor g, int mx, int my) {
		int bw = baseBarW();
		GuiTool.text(g, "基础材料 (" + curBase.size() + ")  滚轮翻动", px + 8, matY - 12, 0xFF7FE0FF);
		GuiTool.panel(g, px + 6, matY, bw, 44, 0x50101018);
		int cols = Math.max(1, bw / 42);
		int start = Math.min(matScroll, Math.max(0, curBase.size() - cols));
		for (int i = 0; i < cols && start + i < curBase.size(); i++) {
			QiuJie.Cai c = curBase.get(start + i);
			int cx = px + 10 + i * 42;
			boolean hov = GuiTool.hovered(mx, my, cx, matY, 40, 40);
			if (hov) GuiTool.panel(g, cx, matY, 40, 40, 0x30406080);
			GuiTool.item(g, c.key, cx + 2, matY + 1);
			GuiTool.text(g, String.valueOf(c.count), cx + 12, matY + 24, 0xFFFFFFFF);
		}
	}

	// tooltip 交给原版那套，Jade/REI 之类的挂在上面的模组也照样能出提示
	private void drawTip(GuiGraphicsExtractor g, int mx, int my) {
		if (my < listTop || my >= listBottom) return;
		if (mx >= px + 6 && mx < px + third - 6) {
			int row = (my - listTop - 2) / ROW + leftScroll;
			if (row >= 0 && row < leftView.size()) {
				WuPin wp = ShuJuKu.INS.get(leftView.get(row).ming);
				if (wp != null) GuiTool.itemTip(g, wp.base, mx, my);
			}
		} else if (mx >= px + third + 6 && mx < px + third + 6 + rightListW()) {
			int row = (my - listTop - 2 - rightHead()) / ROW + rightScroll;
			if (row >= 0 && row < rightView.size()) {
				WuPin wp = rightView.get(row);
				if (wp != null) GuiTool.itemTip(g, wp.base, mx, my);
			}
		}
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
		int cx = (int) e.x(), cy = (int) e.y();
		int button = e.button();
		// 点到搜索框：先显式把容器焦点交过去，再走原版分发。
		// 有输入法/别的模组改动焦点链时，不主动这一步可能点了也 focus 不上（表现：光标在框里却打不了字）
		if (GuiTool.hovered(cx, cy, px + 8, py + 50, third - 16, 20)) this.setFocused(searchLeft);
		else if (GuiTool.hovered(cx, cy, px + third + 8, py + 50, third * 2 - 16, 20)) this.setFocused(searchRight);
		// 右键搜索框直接清空，JEI 就是这个习惯
		if (button == 1 && cy >= py + 50 && cy < py + 70) {
			if (cx >= px + 8 && cx < px + third - 8) { searchLeft.setValue(""); return true; }
			if (cx >= px + third + 8 && cx < px + pw - 8) {
				if (usesKey != null) { usesKey = null; lastRightQ = "\u0000"; refreshViews(); }
				else searchRight.setValue("");
				return true;
			}
		}
		if (cy >= listTop && cy < listBottom) {
			int rowL = (cy - listTop - 2) / ROW + leftScroll;
			int rowR = (cy - listTop - 2 - rightHead()) / ROW + rightScroll;
			if (cx >= px + 6 && cx < px + third - 6 && rowL >= 0 && rowL < leftView.size()) {
				QiuJie.Target t = leftView.get(rowL);
				selLeft = ShuJuKu.INS.get(t.ming);
				if (button == 1) {
					openUses(t.ming);          // 右键看哪些成品要它
				} else {
					openRecipe(selLeft, true); // 左键看它的做法
					if (doubleClick) removeSel();
				}
				return true;
			}
			if (cx >= px + third + 6 && cx < px + third + 6 + rightListW() && rowR >= 0 && rowR < rightView.size()) {
				WuPin wp = rightView.get(rowR);
				selRight = wp;
				if (button == 1) {
					openUses(wp.ming);
				} else {
					openRecipe(wp, false);
					if (doubleClick) addSel();
				}
				return true;
			}
			// 配方面板里的材料：能展开就往里走一层
			if (showRecipe && cx >= recipeX()) {
				PeiFang pf = curPf();
				if (pf != null) {
					List<String> grid = gridOf(pf);
					for (int i = 0; i < 9; i++) {
						int gx = recipeX() + 22 + (i % 3) * 46, gy = listTop + 48 + (i / 3) * 46;
						String k = grid.get(i);
						if (k != null && GuiTool.hovered(cx, cy, gx, gy, 44, 44)) {
							WuPin next = ShuJuKu.INS.get(k);
							if (next != null && next.keHeCheng() && next != recipeTarget) {
								recipeHistory.push(recipeTarget);
								recipeTarget = next; recipePage = 0;
							} else {
								openUses(k);
							}
							return true;
						}
					}
				}
				if (cx >= recipeX() + RECIPE_W - 32 && cy < listTop + 24 && !recipeHistory.isEmpty()) {
					recipeTarget = recipeHistory.pop(); recipePage = 0; return true;
				}
			}
		}
		return super.mouseClicked(e, doubleClick);
	}

	private PeiFang curPf() {
		if (recipeTarget == null) return null;
		List<PeiFang> pfs = new ArrayList<>();
		if (recipeTarget.gongFang != null) pfs.add(recipeTarget.gongFang);
		if (recipeTarget.moJing != null) pfs.add(recipeTarget.moJing);
		return pfs.isEmpty() ? null : pfs.get(Math.max(0, Math.min(recipePage, pfs.size() - 1)));
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double dx, double dy) {
		int dir = dy > 0 ? -1 : 1;
		if (my >= matY && my < matY + 44 && mx >= px + 6 && mx < px + pw - 8) {
			matScroll += dir;
		} else if (showRecipe && mx >= recipeX() && mx < recipeX() + RECIPE_W && my >= listTop && my < listBottom) {
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
			if (usesKey != null) { usesKey = null; lastRightQ = "\u0000"; refreshViews(); return true; }
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
