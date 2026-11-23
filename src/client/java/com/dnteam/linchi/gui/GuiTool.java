package com.dnteam.linchi.gui;

import com.dnteam.linchi.TuBiao;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

// 画图小工具（26.x 的 GuiGraphicsExtractor 版）
public class GuiTool {
	public static final Identifier ICON = Identifier.parse("linchi:icon.png");
	private static boolean screenOpen = false;
	public static void setOpen(boolean v) { screenOpen = v; }
	public static boolean hasOpenScreen() { return screenOpen; }

	public static void panel(GuiGraphicsExtractor g, int x, int y, int w, int h, int argb) {
		g.fill(x, y, x + w, y + h, argb);
	}

	public static void border(GuiGraphicsExtractor g, int x, int y, int w, int h, int color) {
		g.fill(x, y, x + w, y + 1, color);
		g.fill(x, y + h - 1, x + w, y + h, color);
		g.fill(x, y, x + 1, y + h, color);
		g.fill(x + w - 1, y, x + w, y + h, color);
	}

	// 直接传 String，不包 Component —— 每帧几十个字面量，省点分配
	public static void text(GuiGraphicsExtractor g, String s, int x, int y, int color) {
		g.text(Minecraft.getInstance().font, s == null ? "" : s, x, y, color);
	}

	// 鼠标在不在矩形里，手势判断到处都用
	public static boolean hovered(double mx, double my, int x, int y, int w, int h) {
		return mx >= x && mx < x + w && my >= y && my < y + h;
	}

	public static void item(GuiGraphicsExtractor g, String key, int x, int y) {
		ItemStack st = TuBiao.of(key);
		if (!st.isEmpty()) g.item(st, x, y);
	}

	// 物品提示：交给原版渲染，Jade 之类的模组也能正常挂上来
	public static void itemTip(GuiGraphicsExtractor g, String key, int mx, int my) {
		ItemStack st = TuBiao.of(key);
		if (!st.isEmpty()) g.setTooltipForNextFrame(Minecraft.getInstance().font, st, (int) mx, (int) my);
	}

	public static void textTip(GuiGraphicsExtractor g, String line, int mx, int my) {
		g.setTooltipForNextFrame(Component.literal(line == null ? "" : line), (int) mx, (int) my);
	}

	public static void modIcon(GuiGraphicsExtractor g, int x, int y, int size) {
		if (size <= 0) return;
		try {
			g.blit(ICON, x, y, size, size, 0, 0, 256, 256);
		} catch (Exception e) {
			g.fill(x, y, x + size, y + size, 0xFF555555);
		}
	}
}
