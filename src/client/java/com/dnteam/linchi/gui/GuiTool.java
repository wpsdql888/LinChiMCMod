package com.dnteam.linchi.gui;

import com.dnteam.linchi.ShuJuKu;
import com.dnteam.linchi.TuBiao;
import com.dnteam.linchi.WuPin;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

// 画图小工具（26.x 的 GuiGraphicsExtractor 版）
public class GuiTool {
	public static final Identifier ICON = Identifier.parse("linchi:icon.png");
	// 原版按钮/关闭钮贴图，工作台那边直接贴，省得自己画黑条
	public static final Identifier BTN = Identifier.parse("minecraft:widget/button");
	public static final Identifier BTN_HI = Identifier.parse("minecraft:widget/button_highlighted");
	public static final Identifier CROSS = Identifier.parse("minecraft:widget/cross_button");
	public static final Identifier CROSS_HI = Identifier.parse("minecraft:widget/cross_button_highlighted");
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
		item(g, key, x, y, 16);
	}

	// 列表/HUD 里图标缩小一点，重排后给文字留位置
	public static void item(GuiGraphicsExtractor g, String key, int x, int y, int px) {
		ItemStack st = TuBiao.of(key);
		if (st.isEmpty()) return;
		if (px == 16 || px <= 0) { g.item(st, x, y); return; }
		float k = px / 16f;
		g.pose().pushMatrix();
		g.pose().scale(k, k);
		g.item(st, Math.round(x / k), Math.round(y / k));
		g.pose().popMatrix();
	}

	// 物品提示：交给原版渲染，Jade 之类的模组也能正常挂上来
	public static void itemTip(GuiGraphicsExtractor g, String key, int mx, int my) {
		WuPin wp = ShuJuKu.INS.get(key);
		// 魔法物品的原版 base 是随便借来的（铁皮=铁粒），直接丢 ItemStack 会显示成“铁粒”。
		// 这里按命名出提示，跟库里、HUD 对上。
		if (wp != null) {
			g.setTooltipForNextFrame(Component.literal(wp.ming), (int) mx, (int) my);
			return;
		}
		ItemStack st = TuBiao.of(key);
		if (!st.isEmpty()) g.setTooltipForNextFrame(Minecraft.getInstance().font, st, (int) mx, (int) my);
	}

	// 原版九宫格按钮贴图，工作台右侧那几行用它
	public static void button(GuiGraphicsExtractor g, int x, int y, int w, int h, boolean hov) {
		g.blitSprite(RenderPipelines.GUI_TEXTURED, hov ? BTN_HI : BTN, x, y, w, h);
	}

	public static void cross(GuiGraphicsExtractor g, int x, int y, boolean hov) {
		g.blitSprite(RenderPipelines.GUI_TEXTURED, hov ? CROSS_HI : CROSS, x, y, 12, 12);
	}

	public static void textTip(GuiGraphicsExtractor g, String line, int mx, int my) {
		g.setTooltipForNextFrame(Component.literal(line == null ? "" : line), (int) mx, (int) my);
	}

	public static void modIcon(GuiGraphicsExtractor g, int x, int y, int size) {
		if (size <= 0) return;
		try {
			// 26.x 这个重载是 (x0,y0,x1,y1,u0,u1,v0,v1)，UV 是 0~1。
			// 旧写法把 size 当成了 x1/y1、把 256 当成了 UV，结果整块采样到白边，
			// 配置面板左上角才一直是个白方块。
			g.blit(ICON, x, y, x + size, y + size, 0f, 1f, 0f, 1f);
		} catch (Exception e) {
			g.fill(x, y, x + size, y + size, 0xFF555555);
		}
	}
}
