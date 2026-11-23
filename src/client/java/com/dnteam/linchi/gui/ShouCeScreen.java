package com.dnteam.linchi.gui;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

// 游戏内使用手册：9 页翻页，首次进游戏自动弹一次，之后可在 CTRL+P 配置面板里重开。
// 风格与配置面板一致（全屏暗底 + 左上角图标 + 顶部版权鸣谢）。
public class ShouCeScreen extends Screen {
	private final Screen parent;
	private int page = 0;

	private Button prev;
	private Button next;
	private Button pageBtn;

	public ShouCeScreen(Screen parent) {
		super(Component.literal("LinChi 使用手册"));
		this.parent = parent;
	}

	// 每页：{标题, 段落1, 段落2, ...}，段落会按面板宽度自动折行
	private static final String[][] PAGES = {
		{
			"第 1 页 / 欢迎",
			"LinChi 材料计算器",
			"一款服务于装载有 LinsMagic 插件的服务器玩家的便携易用方便计算器。",
			"模组作者：wpsdql888    信息提供：Lin",
			"©2026 DNT Group (https://dnteam.top)",
			"本手册可在配置面板（CTRL+P）里随时重新查看。"
		},
		{
			"第 2 页 / 按键",
			"CTRL + P    打开 / 关闭 配置面板",
			"N            打开 / 关闭 材料板",
			"以上为默认按键，可以在 选项 → 控制 → 「LinChi 材料计算器」分类 里改成任意键。",
			"注意：默认的 P 键需要按住 CTRL 才会生效；改成其他键后，单独按新键即可打开，无需再按 CTRL。"
		},
		{
			"第 3 页 / 配置面板（CTRL+P）",
			"顶部信息：版权、简介、鸣谢作者与信息提供人。",
			"四个开关：材料信息HUD（左下缺料清单）、辅助合成HUD（右下合成步骤）、版本检测（多人更新提醒）、图标附魔光效。",
			"七个数值（点 < 或 > 调整，自动保存）：HUD行数 / 合成步数 / 刷新秒数 / 背景透明度 / 聊天留高 / 横向偏移 / 提示上下。后三项用于避开别的 HUD。",
			"按 ESC 关闭面板。所有设置即时保存到 config/linchi.json。"
		},
		{
			"第 4 页 / 材料板 · 基础（N）",
			"顶部三个按钮：材料信息HUD 开/关、复制材料清单、辅助合成 开/关。",
			"左栏：已经添加的目标物品。右栏：全部魔法物品（2160 种，图标 + 中文名）。",
			"两栏各有一个搜索框，输入关键字即时过滤。",
			"在右栏选中物品后点「添加」加入目标；在左栏选中后可用「删除」「数量-」「数量+」调整。",
			"底部横条显示全部目标汇总后的基础材料。按 N 或 ESC 关闭材料板。"
		},
		{
			"第 5 页 / 材料板 · 配方与手势（仿物品管理器）",
			"左键点物品    查看它的合成配方",
			"右键点物品    查看它的用途（哪些成品会用到它）",
			"双击右栏物品  快速添加；双击左栏物品  快速删除。",
			"配方面板：工作台 / 魔镜两种配方可用滚轮来回切换；点 3×3 材料格里的某个材料可继续展开它的配方；点左上角「<-」或按 Backspace 退回上一层。",
			"其他手势：滚轮在左右栏、底部材料条上各自独立翻动；CTRL+F 聚焦右侧搜索框；右键搜索框清空内容；Home / End / PageUp / PageDown 快速翻页；鼠标悬停物品显示原版提示。"
		},
		{
			"第 6 页 / 两种 HUD",
			"材料信息HUD（屏幕左下）：竖排显示最多 6 个基础材料（数量可调），每项为 图标 + 名称 + 红色缺失数量；自动扫描背包和潜影盒内的物品；约每 3 秒刷新一次（间隔可调）；材料集齐后该行自动消失、下面的顶上来；全部集齐时屏幕中上方提示「材料全部收集完毕 可以进行合成」。",
			"辅助合成HUD（屏幕右下）：竖排显示最多 6 步（数量可调），每步为 目标图标 + 名称 + 剩余数量。",
			"提示：按 F1 可临时隐藏所有 HUD。若 HUD 与聊天或热键栏重叠，请到配置面板调「聊天留高」「横向偏移」。"
		},
		{
			"第 7 页 / 工作台一键填入",
			"打开原版工作台后，界面右侧出现填入列表（最多 3 行），每行是：目标物品 + 剩余数量 +「填入」按钮。",
			"点击「填入」    摆放一组（每格最多 64 个）",
			"Shift + 点击    尽量多填几组",
			"在列表上滚滚轮  翻看更多合成步骤",
			"悬停「填入」    查看这一步需要哪些材料",
			"缺料时会静默跳过，不会弹提示打扰。填入走正规容器点击流程，会同步到服务器，多人游戏同样有效。"
		},
		{
			"第 8 页 / 版本检测",
			"仅在多人游戏时检查更新。",
			"若发现新版本，聊天栏会出现提示，点击其中的链接即可跳转到更新说明页面。",
			"检查结果缓存 24 小时，期间不会重复向服务器请求。",
			"如不需要，可在配置面板关闭「版本检测」。"
		},
		{
			"第 9 页 / 关于",
			"LinChi 材料计算器    1.0.0",
			"模组作者：wpsdql888    信息提供：Lin",
			"版权：©2026 DNT Group (https://dnteam.top)",
			"项目地址：https://github.com/wpsdql888/LinChiMCMod",
			"感谢使用。"
		}
	};

	@Override
	protected void init() {
		int cx = this.width / 2;
		int by = this.height - 34;
		prev = addRenderableWidget(Button.builder(Component.literal("<< 上一页"), b -> go(-1))
			.bounds(cx - 196, by, 90, 20).build());
		pageBtn = addRenderableWidget(Button.builder(Component.literal(""), b -> {})
			.bounds(cx - 100, by, 100, 20).build());
		next = addRenderableWidget(Button.builder(Component.literal("下一页 >>"), b -> go(1))
			.bounds(cx + 6, by, 90, 20).build());
		addRenderableWidget(Button.builder(Component.literal("关闭"), b -> onClose())
			.bounds(cx + 102, by, 90, 20).build());
		sync();
	}

	private void go(int d) {
		page = Math.max(0, Math.min(PAGES.length - 1, page + d));
		sync();
	}

	private void sync() {
		if (prev != null) prev.active = page > 0;
		if (next != null) next.active = page < PAGES.length - 1;
		if (pageBtn != null) pageBtn.setMessage(Component.literal("第 " + (page + 1) + " / " + PAGES.length + " 页"));
	}

	// 按像素宽度把一段文字折成多行（按字符拆，兼容中文无空格的情况）
	private java.util.List<String> wrap(String s, int maxW) {
		java.util.List<String> out = new java.util.ArrayList<>();
		if (s == null || s.isEmpty()) { out.add(""); return out; }
		net.minecraft.client.gui.Font font = Minecraft.getInstance().font;
		StringBuilder line = new StringBuilder();
		for (char c : s.toCharArray()) {
			if (c == '\n') { out.add(line.toString()); line.setLength(0); continue; }
			String test = line.toString() + c;
			if (font.width(test) > maxW && line.length() > 0) {
				out.add(line.toString());
				line.setLength(0);
				line.append(c);
			} else {
				line.append(c);
			}
		}
		if (line.length() > 0) out.add(line.toString());
		return out;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
		this.extractBackground(g, mx, my, delta);
		GuiTool.panel(g, 0, 0, this.width, this.height, 0xDD0A0C14);

		int panelW = Math.min(this.width - 100, 760);
		int panelX = (this.width - panelW) / 2;
		int panelY = 96;
		int panelH = Math.max(120, this.height - panelY - 58);

		GuiTool.modIcon(g, 20, 14, 44);
		GuiTool.text(g, "LinChi 材料计算器 - 使用手册", 76, 18, 0xFFFFFFFF);
		GuiTool.text(g, "©2026 DNT Group (https://dnteam.top)", 76, 38, 0xFFAAAAAA);

		GuiTool.panel(g, panelX, panelY, panelW, panelH, 0xEE161A26);
		GuiTool.border(g, panelX, panelY, panelW, panelH, 0xFF4A6A9A);

		String[] pg = PAGES[page];
		int x = panelX + 24;
		int maxTextW = panelW - 48;
		int y = panelY + 18;
		GuiTool.text(g, pg[0], x, y, 0xFFFFD54F);
		y += 28;

		for (int i = 1; i < pg.length && y < panelY + panelH - 16; i++) {
			int color = (i == 1) ? 0xFF9AD0FF : 0xFFE0E0E0;
			for (String line : wrap(pg[i], maxTextW)) {
				if (y > panelY + panelH - 20) break;
				GuiTool.text(g, line, x, y, color);
				y += 16;
			}
			y += 6; // 段间距
		}

		super.extractRenderState(g, mx, my, delta);
		GuiTool.text(g, "← → 翻页    ESC 关闭    首次安装自动弹出一次，之后可在配置面板重看", this.width / 2 - 200, this.height - 52, 0xFF888888);
	}

	@Override
	public boolean keyPressed(KeyEvent e) {
		if (e.key() == InputConstants.KEY_ESCAPE) { onClose(); return true; }
		if (e.key() == InputConstants.KEY_LEFT || e.key() == InputConstants.KEY_PAGEUP) { go(-1); return true; }
		if (e.key() == InputConstants.KEY_RIGHT || e.key() == InputConstants.KEY_PAGEDOWN) { go(1); return true; }
		return super.keyPressed(e);
	}

	@Override
	public void onClose() {
		Minecraft.getInstance().setScreenAndShow(parent);
	}
}
