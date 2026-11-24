package com.dnteam.linchi;

import com.dnteam.linchi.gui.GuiTool;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

// 工作台右侧的单步向导：一次只显示当前这一步，填完再走下一步
// 渲染给 Mixin 调，鼠标走 Fabric 的 ScreenMouseEvents
public class GongFangZhiYin {
	private static final int MIN_W = 96;
	private static final int MAX_W = 150;
	private static final int LINE_H = 10;
	private static final int BTN_H = 18;

	// 合成界面不是永远居中：开了配方书 leftPos 会往右挪。
	// 缓存 AbstractContainerScreen 的 leftPos/topPos，跟着原版界面走。
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

	// 步骤列表来自求解器，按依赖层级排好；done 记录本次目标已经走过的步骤
	private static final List<QiuJie.Bu> steps = new ArrayList<>();
	private static final Set<String> done = new HashSet<>();
	private static int idx = 0;
	private static String sig = "";
	private static boolean autoDone = false;
	private static String blockMsg = "";

	private static final int[] L = new int[4];
	private static final int[] B1 = new int[4];
	private static final int[] B2 = new int[4];

	private static QiuJie.Bu current() {
		return idx >= 0 && idx < steps.size() ? steps.get(idx) : null;
	}

	private static boolean manual(QiuJie.Bu bu) {
		return bu == null || bu.pf == null || bu.pf.mirror;
	}

	private static int owned(QiuJie.Bu bu) {
		return ZhuangTai.INS.jiShu == null ? 0 : ZhuangTai.INS.jiShu.mingYou(bu.ming);
	}

	private static int remain(QiuJie.Bu bu) {
		return Math.max(0, bu.need - owned(bu));
	}

	private static String signature() {
		StringBuilder sb = new StringBuilder();
		for (QiuJie.Target t : ZhuangTai.INS.targets)
			sb.append(t.ming).append('\u0001').append(t.count).append('\u0002');
		for (QiuJie.Bu bu : ZhuangTai.INS.latest.crafts.values())
			sb.append(bu.ming).append('\u0003').append(bu.need).append('\u0004');
		return sb.toString();
	}

	private static void refresh() {
		String s = signature();
		steps.clear();
		for (QiuJie.Bu bu : ZhuangTai.INS.latest.crafts.values()) steps.add(bu);

		if (!s.equals(sig)) {
			sig = s;
			done.clear();
			idx = 0;
			autoDone = false;
			blockMsg = "";
			// 进工作台时产物已经在背包里的步骤直接算完成
			for (QiuJie.Bu bu : steps) if (remain(bu) <= 0) done.add(bu.ming);
		}

		QiuJie.Bu cur = current();
		// 工作台步骤产物检测到了就标记完成，停在当前步等玩家点“下一步”
		if (cur != null && !manual(cur) && !done.contains(cur.ming) && remain(cur) <= 0) {
			done.add(cur.ming);
			autoDone = true;
		}
		// 拦截提示里的数量过时了就清掉，别一直挂着旧数字
		if (!blockMsg.isEmpty() && cur != null && owned(cur) >= cur.need) blockMsg = "";
		if (!autoDone) {
			while (idx < steps.size() && done.contains(steps.get(idx).ming)) idx++;
		}
		if (idx >= steps.size()) autoDone = false;
	}

	private static void advance() {
		if (idx < steps.size()) done.add(steps.get(idx).ming);
		idx++;
		autoDone = false;
		blockMsg = "";
		while (idx < steps.size() && done.contains(steps.get(idx).ming)) idx++;
	}

	private static void tryNext(QiuJie.Bu cur) {
		int need = cur.need;
		// 点下一步时现扫一次，别用 250ms 前可能还没刷新的缓存
		int have = BeiBao.scan().mingYou(cur.ming);
		if (have >= need) {
			done.add(cur.ming);
			advance();
		} else {
			blockMsg = "背包里 " + have + "/" + need + "，请先放入背包或潜影盒";
		}
	}

	private static int[] layout(Screen s) {
		int x = leftOf(s) + 176 + 6;
		int right = s.width - 4;
		int w = Math.min(MAX_W, Math.max(MIN_W, right - x));
		// 右侧空间实在不够就贴右边缘，宁可压住一点界面也不挪到别处
		if (x + w > right) {
			w = MIN_W;
			x = Math.max(4, right - w);
		}
		int y = topOf(s) + 6;

		boolean allDone = idx >= steps.size();
		QiuJie.Bu cur = current();
		boolean manual = cur != null && manual(cur);
		boolean warn = !allDone && !ZhuangTai.INS.hudRows().isEmpty();
		int textLines = allDone ? 2 : (manual ? 3 : 2);

		int h;
		if (allDone) {
			h = 6 + textLines * LINE_H + 6;
		} else {
			h = 6 + textLines * LINE_H + (warn ? LINE_H : 0) + 4 + BTN_H;
			if (manual) h += 15;
			h += 4 + 9 + 4;
		}
		L[0] = x; L[1] = y; L[2] = w; L[3] = h;

		int by = y + 6 + textLines * LINE_H + (warn ? LINE_H : 0) + 3;
		B1[0] = x + 4; B1[1] = by; B1[2] = w - 8; B1[3] = BTN_H;
		B2[0] = x + 4; B2[1] = by + BTN_H + 2; B2[2] = w - 8; B2[3] = 13;
		return L;
	}

	private static String obtainText(QiuJie.Bu bu) {
		WuPin wp = ShuJuKu.INS.get(bu.ming);
		if (wp != null && wp.huoQu != null && !wp.huoQu.isBlank()) return wp.huoQu;
		if (bu.pf != null && bu.pf.mirror) return "魔镜合成";
		return "按服务器要求完成本步";
	}

	private static void addGrid(List<String> lines, PeiFang pf) {
		List<String> grid = pf.jiuGe();
		for (int k = 0; k < 9; k++) {
			if (grid.get(k) != null) lines.add("第" + (k + 1) + "格: " + TuBiao.mingOf(grid.get(k)));
		}
	}

	public static void draw(GuiGraphicsExtractor g, Screen s, int mx, int my) {
		if (!PeiZhi.INS.zhuGongKai) return;
		if (ZhuangTai.INS.targets.isEmpty()) return;
		refresh();
		if (steps.isEmpty()) return;
		int[] pl = layout(s);
		int x = pl[0], y = pl[1], w = pl[2], h = pl[3];
		GuiTool.panel(g, x, y, w, h, 0xCC101018);

		boolean allDone = idx >= steps.size();
		QiuJie.Bu cur = current();
		boolean manual = cur != null && manual(cur);
		boolean warn = !allDone && !ZhuangTai.INS.hudRows().isEmpty();
		int tx = x + 6, ty = y + 6;

		GuiTool.text(g, allDone ? "全部完成" : "第 " + (idx + 1) + "/" + steps.size() + " 步",
			tx, ty, 0xFFFFD54F);

		if (allDone) {
			GuiTool.text(g, "所有步骤已完成", tx, ty + LINE_H, 0xFF7FE0FF);
		} else {
			boolean curDone = done.contains(cur.ming) || remain(cur) <= 0;
			String line;
			if (manual) line = "操作: " + cur.ming;
			else line = curDone ? "本步完成: " + cur.ming : "合成 " + cur.ming + " ×" + remain(cur);
			GuiTool.text(g, fit(line, w - 12), tx, ty + LINE_H, 0xFFFFFFFF);

			if (manual) {
				GuiTool.text(g, fit(obtainText(cur), w - 12), tx, ty + LINE_H * 2, 0xFFAAAAAA);
			}
			if (warn) {
				int wy = ty + (manual ? 3 : 2) * LINE_H;
				GuiTool.text(g, "基础材料还缺 " + ZhuangTai.INS.hudRows().size() + " 种", tx, wy, 0xFFFF8888);
			}

			String label = manual ? "下一步" : (curDone ? "下一步" : "填入本步");
			drawButton(g, B1, label, mx, my);
			if (manual) drawButton(g, B2, "强制下一步", mx, my);

			String hint = manual
				? (blockMsg.isEmpty() ? "完成后放入背包或潜影盒" : blockMsg)
				: "只填本步剩余量，单次最多64组";
			int hy = B1[1] + BTN_H + (manual ? 15 : 0) + 3;
			GuiTool.text(g, fit(hint, w - 12), tx, hy, blockMsg.isEmpty() ? 0xFF888888 : 0xFFFF6666);
		}

		tooltip(g, mx, my, x, y, w, h, allDone, cur, manual);
	}

	private static void drawButton(GuiGraphicsExtractor g, int[] r, String label, int mx, int my) {
		boolean hov = GuiTool.hovered(mx, my, r[0], r[1], r[2], r[3]);
		GuiTool.button(g, r[0], r[1], r[2], r[3], hov);
		int tw = Minecraft.getInstance().font.width(label);
		GuiTool.text(g, label, r[0] + (r[2] - tw) / 2, r[1] + (r[3] - 8) / 2, 0xFFFFFFFF);
	}

	private static void tooltip(GuiGraphicsExtractor g, int mx, int my, int x, int y, int w, int h,
								boolean allDone, QiuJie.Bu cur, boolean manual) {
		if (!GuiTool.hovered(mx, my, x, y, w, h)) return;
		List<String> lines = new ArrayList<>();
		if (allDone) {
			lines.add("全部完成");
		} else {
			lines.add("第 " + (idx + 1) + "/" + steps.size() + " 步");
			if (manual) {
				lines.add("操作: " + cur.ming);
				String ob = obtainText(cur);
				if (ob != null && !ob.isBlank()) lines.add(ob);
				if (cur.pf != null) addGrid(lines, cur.pf);
				lines.add("背包/潜影盒: " + owned(cur) + " / " + cur.need);
				lines.add("完成后放入背包或潜影盒，再点下一步");
				if (!blockMsg.isEmpty()) lines.add(blockMsg);
			} else {
				lines.add(remain(cur) <= 0 ? "本步已完成" : "合成 " + cur.ming + " ×" + remain(cur));
				if (cur.pf != null) {
					addGrid(lines, cur.pf);
					lines.add("每次产出 " + cur.pf.count + " 个");
				}
				lines.add("只填本步剩余量，单次最多64组");
				if (!ZhuangTai.INS.hudRows().isEmpty())
					lines.add("基础材料还缺 " + ZhuangTai.INS.hudRows().size() + " 种");
			}
		}
		List<Component> comps = new ArrayList<>();
		for (String line : lines) comps.add(Component.literal(line));
		g.setTooltipForNextFrame(Minecraft.getInstance().font, comps, java.util.Optional.empty(), mx, my);
	}

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
		if (ZhuangTai.INS.targets.isEmpty()) return false;
		refresh();
		if (steps.isEmpty()) return false;
		int[] pl = layout(s);
		if (!GuiTool.hovered(e.x(), e.y(), pl[0], pl[1], pl[2], pl[3])) return false;

		if (idx >= steps.size()) return true;
		QiuJie.Bu cur = current();
		if (cur == null) return true;

		if (GuiTool.hovered(e.x(), e.y(), B1[0], B1[1], B1[2], B1[3])) {
			if (manual(cur)) {
				tryNext(cur);
			} else if (done.contains(cur.ming) || remain(cur) <= 0) {
				advance();
			} else {
				TianRu.fillMax(cur);
			}
		} else if (manual(cur) && GuiTool.hovered(e.x(), e.y(), B2[0], B2[1], B2[2], B2[3])) {
			done.add(cur.ming);
			advance();
		}
		return true;
	}

	// 单步向导不需要滚轮翻页，全部交给工作台本身
	public static boolean onScroll(Screen s, double mx, double my, double dy) {
		return false;
	}
}
