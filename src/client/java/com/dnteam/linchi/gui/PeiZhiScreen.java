package com.dnteam.linchi.gui;

import com.dnteam.linchi.BanBen;
import com.dnteam.linchi.PeiZhi;
import com.dnteam.linchi.TuBiao;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

// CTRL+P 配置面板：全屏，左上角图标，顶部鸣谢+版权，ESC/P 关闭
public class PeiZhiScreen extends Screen {
	private final Screen parent;
	private int rowY0, rowGap, colW, btnY;
	private String updateMsg = "";

	public PeiZhiScreen(Screen parent) {
		super(Component.literal("LinsMagic 配置"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		int cx = this.width / 2;
		// 行数固定 7 行，按屏幕高度摊开；短屏自动压行距，别让底部按钮骑到数值行上
		btnY = this.height - 34;
		int footY = btnY - 16;
		int top = 112;
		int rows = 7;
		int avail = Math.max(rows * 12, footY - 4 - top);
		rowGap = Math.max(12, Math.min(26, avail / rows));
		rowY0 = top;
		colW = Math.max(100, Math.min(170, (this.width - 44) / 2));
		int lx = cx - 6 - colW;
		int rx = cx + 6;

		addToggle(lx, rowY0, "材料信息HUD", () -> PeiZhi.INS.hudKai, v -> PeiZhi.INS.hudKai = v, colW);
		addToggle(lx, rowY0 + rowGap, "辅助合成HUD", () -> PeiZhi.INS.zhuGongKai, v -> PeiZhi.INS.zhuGongKai = v, colW);
		addToggle(lx, rowY0 + rowGap * 2, "版本检测", () -> PeiZhi.INS.jianCeBanBen, v -> PeiZhi.INS.jianCeBanBen = v, colW);
		addToggle(lx, rowY0 + rowGap * 3, "图标附魔光效", () -> PeiZhi.INS.fuMoGuang, v -> {
			PeiZhi.INS.fuMoGuang = v; TuBiao.clearCache();
		}, colW);

		// 手动检测：每次点都联网，不看 24h 缓存
		addRenderableWidget(Button.builder(Component.literal("检测更新"), b -> {
			updateMsg = "检测中...";
			BanBen.manualCheck(Minecraft.getInstance(), msg -> updateMsg = msg);
		}).bounds(lx, rowY0 + rowGap * 4, colW, 20).build());

		addNum(rx, rowY0, "HUD行数", () -> PeiZhi.INS.hudHangShu, v -> PeiZhi.INS.hudHangShu = clamp(v, 1, 12), colW);
		addNum(rx, rowY0 + rowGap, "合成步数", () -> PeiZhi.INS.zhongJianJu, v -> PeiZhi.INS.zhongJianJu = clamp(v, 1, 12), colW);
		addNum(rx, rowY0 + rowGap * 2, "刷新秒数", () -> PeiZhi.INS.gengXinMiao, v -> PeiZhi.INS.gengXinMiao = clamp(v, 1, 30), colW);
		addNum(rx, rowY0 + rowGap * 3, "背景透明度", () -> PeiZhi.INS.hudTouMing, v -> PeiZhi.INS.hudTouMing = clamp(v, 0, 255), colW);
		// 跟投影之类的 HUD 撞上了用这三个挪一挪
		addNum(rx, rowY0 + rowGap * 4, "聊天留高", () -> PeiZhi.INS.hudChatJianJu, v -> PeiZhi.INS.hudChatJianJu = clamp(v, 0, 300), colW);
		addNum(rx, rowY0 + rowGap * 5, "横向偏移", () -> PeiZhi.INS.hudPianYiX, v -> PeiZhi.INS.hudPianYiX = clamp(v, -200, 200), colW);
		addNum(rx, rowY0 + rowGap * 6, "提示上下", () -> PeiZhi.INS.hudPianYiY, v -> PeiZhi.INS.hudPianYiY = clamp(v, -200, 200), colW);

		int bw = Math.min(160, colW);
		addRenderableWidget(Button.builder(Component.literal("保存并关闭"), b -> {
			PeiZhi.INS.save(); Minecraft.getInstance().setScreenAndShow(parent);
		}).bounds(cx - 6 - bw, btnY, bw, 20).build());
		// 随时重看使用手册
		addRenderableWidget(Button.builder(Component.literal("查看使用手册"), b -> {
			Minecraft.getInstance().setScreenAndShow(new ShouCeScreen(this));
		}).bounds(cx + 6, btnY, bw, 20).build());
	}

	int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }
	interface BoolGet { boolean get(); }
	interface BoolSet { void set(boolean v); }
	interface IntGet { int get(); }
	interface IntSet { void set(int v); }

	private void addToggle(int x, int y, String label, BoolGet get, BoolSet set, int w) {
		addRenderableWidget(Button.builder(Component.literal(label + ": " + (get.get() ? "开" : "关")), b -> {
			set.set(!get.get()); PeiZhi.INS.save();
			b.setMessage(Component.literal(label + ": " + (get.get() ? "开" : "关")));
		}).bounds(x, y, w, 20).build());
	}

	// 左右两个箭头，中间显示当前值。原来只能往大调，太别扭
	private void addNum(int x, int y, String label, IntGet get, IntSet set, int w) {
		Button show = Button.builder(Component.literal(label + ": " + get.get()), b -> {})
			.bounds(x + 24, y, Math.max(48, w - 48), 20).build();
		addRenderableWidget(show);
		addRenderableWidget(Button.builder(Component.literal("<"), b -> step(show, label, get, set, -1))
			.bounds(x, y, 22, 20).build());
		addRenderableWidget(Button.builder(Component.literal(">"), b -> step(show, label, get, set, 1))
			.bounds(x + Math.max(48, w - 48) + 24, y, 22, 20).build());
	}

	private void step(Button show, String label, IntGet get, IntSet set, int dir) {
		set.set(get.get() + dir);
		PeiZhi.INS.save();
		show.setMessage(Component.literal(label + ": " + get.get()));
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
		// 不要调用 extractBackground()：父类流程已调用，重复会触发 "Can only blur once per frame" 崩溃
		GuiTool.panel(g, 0, 0, this.width, this.height, 0xCC101018);
		GuiTool.modIcon(g, 16, 12, 40);
		String title = "LinsMagic 材料计算器 - 配置";
		GuiTool.text(g, title, 64, 16, 0xFFFFFFFF);
		GuiTool.text(g, "v" + BanBen.LOCAL, 64 + Minecraft.getInstance().font.width(title) + 10, 16, 0xFF888888);
		GuiTool.text(g, "©2026 DNT Group (https://dnteam.top)", 64, 34, 0xFFAAAAAA);
		GuiTool.text(g, "一款服务于装载有LinsMagic插件的服务器玩家的便携易用方便计算器", 64, 50, 0xFF9AD0FF);
		GuiTool.text(g, "鸣谢", this.width / 2 - 100, 66, 0xFFFFD54F);
		GuiTool.text(g, "模组作者: wpsdql888    信息提供: Lin", this.width / 2 - 140, 78, 0xFFDDDDDD);
		GuiTool.text(g, "Github: github.com/wpsdql888/LinChiMCMod", this.width / 2 - 140, 90, 0xFF9AD0FF);
		super.extractRenderState(g, mx, my, delta);
		int lx = this.width / 2 - 6 - colW;
		int msgY = rowY0 + rowGap * 4 + 24;
		if (!updateMsg.isEmpty() && msgY + 9 <= btnY - 14) {
			GuiTool.text(g, updateMsg, lx, msgY, 0xFF9AD0FF);
		}
		// 屏幕太矮时这行会压到最后一行数值上，宁可不画
		if (rowY0 + rowGap * 6 + 20 <= btnY - 14) {
			GuiTool.text(g, "ESC / CTRL+P 关闭    数值点 < > 调整，热键在原版控制设置里改", 8, btnY - 14, 0xFF888888);
		}
	}

	@Override
	public boolean keyPressed(KeyEvent e) {
		// ESC 关闭。CTRL+P 的关闭交给 tick 里的 toggle 统一处理：
		// 若这里也关，关完同一帧 tick 的 consumeClick 会立刻重开，导致关不掉。
		if (e.key() == InputConstants.KEY_ESCAPE) {
			PeiZhi.INS.save(); Minecraft.getInstance().setScreenAndShow(parent); return true;
		}
		return super.keyPressed(e);
	}

	@Override
	public void onClose() { PeiZhi.INS.save(); GuiTool.setOpen(false); Minecraft.getInstance().setScreenAndShow(parent); }
}
