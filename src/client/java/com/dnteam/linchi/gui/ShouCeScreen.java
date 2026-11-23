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
	// 手册内容结构版本。内容大改就 +1，老玩家的配置里版本号低会再弹一次
	public static final int CUR_VER = 3;

	private final Screen parent;
	private int page = 0;

	// 折行结果缓存：手册内容固定，只有屏宽变了才需要重算。
	// 之前每帧对整页逐字符 font.width + 拼字符串，停留一页时纯属空转。
	private java.util.List<java.util.List<String>> wrapCache;
	private int wrapCacheWidth = -1;

	private Button prev;
	private Button next;
	private Button pageBtn;

	public ShouCeScreen(Screen parent) {
		super(Component.literal("LinsMagic 使用手册"));
		this.parent = parent;
	}

	// 每页：{标题, 段落1, 段落2, ...}，段落会按面板宽度自动折行
	private static final String[][] PAGES = {
		{
			"第 1 页 / 这模组是干嘛的",
			"LinsMagic 材料计算器",
			"你告诉它“我要做 3 个终极熔锻炉核心”，它把这件事拆成两样东西：还缺哪些基础材料，以及按什么顺序去合成。材料清单在屏幕左下，合成顺序在右下，做工作台的时候还能一键把材料摆进去。",
			"数据全部打包在 JAR 里，不联网、不需要服务器装东西。物品库一共 2160 条，来自 LinsMagic 的配方表。",
			"有一点要先说清楚：它认的是物品的显示名字，不是 minecraft:xxx 这种内部代号。服务器把铁粒改名成“铁皮”，那它在计算器里就叫铁皮；背包里找材料也按“铁皮”三个字去找。",
			"模组作者：wpsdql888    信息提供：Lin",
			"©2026 DNT Group (https://dnteam.top)",
			"这本手册第一次进服务器会自动弹一次，之后在 CTRL+P 的配置面板里随时能重看。"
		},
		{
			"第 2 页 / 第一次用",
			"把对应版本的 JAR 丢进 .minecraft/mods，确认装了 Fabric Loader、Fabric API，Java 要 25。版本对不上会在启动器里直接报错，别把 26.3 的 JAR 拿去开 26.1。",
			"进游戏后先按 N 打开材料板。右边那一栏是全部物品，上面有个搜索框，直接打中文名，比如“熔锻炉核心”。",
			"找到目标后单击选中，再点下面的“添加”把它加进左边。双击是打开配方，不会直接添加。要几个就点几次“数量+”。左边的清单会一直留着，关掉材料板再开也还在。",
			"加完之后看屏幕左下角：那里只列还缺的材料。背包每 3 秒扫一次（间隔能改），捡到东西后过几秒数字自己会变。",
			"想调位置、行数、透明度，按 CTRL+P。默认的 P 要按住 CTRL 才算，单独按 P 是原版的社交菜单。"
		},
		{
			"第 3 页 / 材料板：左右两栏",
			"材料板（N）分三块：左边是目标+材料总表，中间是全部物品，右边是配方或用途。第三栏平时不显示，双击物品才会出来。",
			"顶部三个按钮分别是材料信息HUD开关、复制材料清单、辅助合成HUD开关。复制出来的格式是“材料名 x数量”，一行一个，可以直接贴到群里。",
			"左栏现在是目标、可合成中间物、基础材料的总表。单击可合成的行就会展开/收起：收起=当材料收集，展开=继续拆配方；整行都能点，不用非点最右边两个字。双击打开配方。不可合成的行单击只选中，选中目标后可用下面的“删除/数量”按钮调整。",
			"中间栏单击同样只选中；双击打开配方；右键看“哪些成品会用到它”。如果一个物品没有任何成品用到，右键不会弹出空白页，省得你以为卡了。",
			"两个搜索框各管各的：左边搜当前材料，右边搜全库。右键搜索框清空，CTRL+F 直接跳到右边搜索框。",
			"底部横条是所有目标汇总后的基础材料。数量多的往后排，滚轮可以翻。"
		},
		{
			"第 4 页 / 配方和用途怎么看",
			"双击物品后，右边会出现它的配方。工作台配方画成 3×3，魔镜配方按九个格子原样摆。两个都有的时候，鼠标放在配方面板上滚轮切换，顶部会显示 1/2、2/2。",
			"3×3 里的材料点一下就能继续往里展开。比如终极熔锻炉核心要用炎烬余灰，点炎烬余灰就能看它又需要什么。左上角的“<-”或者 Backspace 退回上一层。",
			"配方格子里写的是物品名字，不是 minecraft:iron_ingot。看到“铁锭”就说明这一步要铁锭；如果服务器把铁锭也改了名，这里会跟着显示服务器的名字。",
			"看用途是反过来的：右键某个材料，右边列出所有会用到它的成品。想找“这个东西到底能做什么”，就用这个。",
			"第三栏右上角有个 ×，点它或者右键空白处就能关掉第三栏，回到两栏。不用把整个材料板关了再重开。"
		},
		{
			"第 5 页 / 两个 HUD",
			"材料信息HUD在屏幕左下，只显示“还缺”的基础材料。每行是图标、名字、一个红色数字——数字就是还缺几个，不带“-”也不带“缺”字。背包里够了这一行就消失，下面的顶上来。全部够了会在准星上方弹一句“材料全部收集完毕 可以进行合成”。",
			"辅助合成HUD在屏幕右下，显示合成步骤，从底层往上层排。每行是第几步、做什么、还差几个。做好了或者背包里已经有了，剩余数会跟着减。",
			"两个 HUD 都按物品显示名统计。还是那个例子：铁皮在原版是铁粒，但服务器叫它铁皮，HUD 只数名字叫“铁皮”的那些，不会把普通铁粒算进去。",
			"HUD 只在没有打开任何界面的时候显示。按 F1 会跟原版 HUD 一起隐藏。",
			"如果 HUD 压到聊天框或者热键栏，去 CTRL+P 调“聊天留高”“横向偏移”；嫌太大就调“HUD行数”，或者干脆关掉一个。"
		},
		{
			"第 6 页 / 工作台一键填入",
			"打开原版工作台，合成界面右侧会出现最多三行按钮，用的是原版按钮样式，跟着界面走，不会压在合成格上。窗口窄的时候会自动收窄，右边放不下就挪到界面下方或上方。",
			"每行写着“填入 物品名 ×还差几个”。点一下会自动算：还差几个成品、背包里有多少材料、每格最多 64 个，然后一次尽量填满。比如要做 100 个铁皮，第一次会填够 64 次，合完 64 个后再点一下，会自动补剩下的 36 次，不会多也不会少。",
			"鼠标放在按钮上能看到这一格具体要什么、放第几格、一次产出几个。比如铁皮是竖着两个铁锭，提示会写第 1 格铁锭、第 4 格铁锭。",
			"找材料靠名字：它会在背包里找显示名完全一样的物品，所以服务器改过名的东西也能填。找不到就跳过，不会弹提示，也不会把别的物品塞进去。",
			"限制：只认原版 3×3 工作台。服务器自己做的合成界面、需要特殊容器标题的，暂时不支持；材料放在潜影盒里也不算，先拿到背包里。",
			"点了没反应的话，先确认三件事：工作台是原版的、背包里有同名材料、合成格里没有别的东西。三条都满足还是不动，把 logs/latest.log 里带“填入中断”的那行发出来。"
		},
		{
			"第 7 页 / 配置面板（CTRL+P）",
			"左上角四个开关：材料信息HUD、辅助合成HUD、版本检测、图标附魔光效。前两个就是左右两个 HUD；版本检测只管多人自动更新提醒；附魔光效只给魔法物品加闪光，原版材料不会闪。标题右边会显示当前版本号。",
			"左下角有一个“检测更新”按钮，点一下会立刻联网检查 update.json，并在按钮下面和聊天栏显示结果：已是最新版、发现新版本或检测失败。它不受“版本检测”开关和 24 小时缓存限制。",
			"右边七个数值，点 < 或 > 调，改完立刻存进 config/linchi.json。HUD行数、合成步数决定两个 HUD 最多显示几行；刷新秒数是扫背包的间隔，默认 3 秒，调太小吃性能。",
			"背景透明度 0 是全透明、255 是全黑。聊天留高是给左下聊天框留的空间，横向偏移把 HUD 左右挪，提示上下只管“材料收集完毕”那句话。",
			"屏幕比较矮的时候，配置面板会自动压缩行距，底部按钮不会盖住数值行。按钮被什么挡住了，把窗口拉大一点就行。",
			"面板里还有“查看使用手册”和“保存并关闭”。按 ESC 也会保存。"
		},
		{
			"第 8 页 / 版本检测与常见问题",
			"版本检测只在多人游戏里跑，24 小时内不会重复请求。发现新版本会在聊天栏发一条消息，点链接能跳到更新说明。不需要就在配置里关掉它。",
			"HUD 不显示：先看配置里开关开没开，再看材料板里有没有加目标，最后看是不是正开着箱子/工作台——有界面时 HUD 会整体隐藏，这是故意的。",
			"数字不对：多半是物品名对不上。服务器改过名、或者加了前缀后缀，计算器只认完全一样的显示名。",
			"填入没反应：确认是原版工作台、材料在背包本体不是潜影盒、合成格是空的。还不行就看日志里有没有“填入中断”。",
			"按键没反应：去 选项 → 控制 → 「LinsMagic 材料计算器」分类里看是不是被别的模组抢了。CTRL+P 的 P 需要按住 CTRL；把键改成别的之后，单按新键就行。",
			"启动报 UnsupportedClassVersionError，是 Java 版本太低，换 Java 25。报模组元数据损坏，基本是 JAR 下错版本，重新下对应游戏版本的那个。"
		},
		{
			"第 9 页 / 关于",
			"LinsMagic 材料计算器    1.1.1",
			"对外模组名和仓库名是 LinChiMCMod，mod id 是 linchi；游戏里显示的名字是 LinsMagic 材料计算器。名字分开是故意的，在启动器里找 LinChiMCMod 就对了。",
			"模组作者：wpsdql888    信息提供：Lin",
			"版权：©2026 DNT Group (https://dnteam.top)",
			"项目地址：https://github.com/wpsdql888/LinChiMCMod",
			"物品和配方数据来自 LinsMagic 的 Calculator v1.0.0。数据有错、缺物品，找 Lin；程序上的问题，带日志找 wpsdql888。"
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
		wrapCache = null; // 换页了，折行缓存作废
		sync();
	}

	private void sync() {
		if (prev != null) prev.active = page > 0;
		if (next != null) next.active = page < PAGES.length - 1;
		if (pageBtn != null) pageBtn.setMessage(Component.literal("第 " + (page + 1) + " / " + PAGES.length + " 页"));
	}

	// 当前页所有段落的折行结果，屏宽变了自动重算
	private java.util.List<java.util.List<String>> pageLines(int maxW, int screenW) {
		if (wrapCache != null && wrapCacheWidth == screenW) return wrapCache;
		wrapCacheWidth = screenW;
		wrapCache = new java.util.ArrayList<>();
		String[] pg = PAGES[page];
		for (int i = 1; i < pg.length; i++) wrapCache.add(wrap(pg[i], maxW));
		return wrapCache;
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
		// 不要在这里调用 extractBackground()：26.x 的 Screen.extractRenderStateWithTooltipAndSubtitles
		// 已经先调用过一次，重复调用会在同一帧触发两次背景模糊，抛 "Can only blur once per frame" 崩溃。
		GuiTool.panel(g, 0, 0, this.width, this.height, 0xDD0A0C14);

		int panelW = Math.min(this.width - 100, 760);
		int panelX = (this.width - panelW) / 2;
		int panelY = 88;
		int panelH = Math.max(120, this.height - panelY - 46);

		GuiTool.modIcon(g, 16, 12, 40);
		GuiTool.text(g, "LinsMagic 材料计算器 - 使用手册", 64, 16, 0xFFFFFFFF);
		GuiTool.text(g, "©2026 DNT Group (https://dnteam.top)", 64, 36, 0xFFAAAAAA);

		GuiTool.panel(g, panelX, panelY, panelW, panelH, 0xEE161A26);
		GuiTool.border(g, panelX, panelY, panelW, panelH, 0xFF4A6A9A);

		String[] pg = PAGES[page];
		int x = panelX + 24;
		int maxTextW = panelW - 48;
		int y = panelY + 14;
		GuiTool.text(g, pg[0], x, y, 0xFFFFD54F);
		y += 24;

		java.util.List<java.util.List<String>> lines = pageLines(maxTextW, this.width);
		for (int i = 0; i < lines.size() && y < panelY + panelH - 14; i++) {
			int color = (i == 0) ? 0xFF9AD0FF : 0xFFE0E0E0;
			for (String line : lines.get(i)) {
				if (y > panelY + panelH - 18) break;
				GuiTool.text(g, line, x, y, color);
				y += 14;
			}
			y += 4; // 段间距
		}

		super.extractRenderState(g, mx, my, delta);
		GuiTool.text(g, "← → 翻页    ESC 关闭    首次安装自动弹出一次，之后可在配置面板重看", this.width / 2 - 200, this.height - 12, 0xFF888888);
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
		// 看完（或关掉）才标记版本号并保存：弹出瞬间崩溃的话，下次进游戏还能再弹
		com.dnteam.linchi.PeiZhi.INS.shouCeVer = CUR_VER;
		com.dnteam.linchi.PeiZhi.INS.save();
		Minecraft.getInstance().setScreenAndShow(parent);
	}
}
