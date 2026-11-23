package com.dnteam.linchi;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

// 客户端入口：按键、事件都在这挂
public class LinChiClient implements ClientModInitializer {
	public static final KeyMapping.Category CAT = KeyMapping.Category.register(Identifier.parse("linchi:main"));

	public static KeyMapping keyPeiZhi;
	public static KeyMapping keyCaiLiao;

	@Override
	public void onInitializeClient() {
		// 首次安装先建数据库版本文件，默认 1.0.0
		ShuJuGengXin.init();
		// 用 InputConstants 常量而不是硬编码 80/78：MC 26.3 的键码编号与 26.1/26.2
		// 不同（KEY_P 从 80 变成 19）。写死数字会让 26.3 的默认绑定与下面的
		// KEY_P 判断对不上，导致 CTRL+P 退化成单按 P 就能打开面板。
		keyPeiZhi = KeyMappingHelper.registerKeyMapping(new KeyMapping(
			"key.linchi.peizhi", InputConstants.KEY_P, CAT));
		keyCaiLiao = KeyMappingHelper.registerKeyMapping(new KeyMapping(
			"key.linchi.cailiao", InputConstants.KEY_N, CAT));

		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			// 背包扫描放 tick 里，按配置的秒数走。渲染那边只读结果
			if (mc.player != null && mc.level != null) ZhuangTai.INS.tick();
			// 首次安装进入世界后自动弹一次使用手册；只在没有其它界面时弹，避免打断玩家。
			// “已看过”的标记在手册关闭时才写入（见 ShouCeScreen.onClose），
			// 这样即使弹出时游戏崩溃，下次进游戏仍会再弹一次。
			if (PeiZhi.INS.shouCeVer < com.dnteam.linchi.gui.ShouCeScreen.CUR_VER
				&& mc.player != null && mc.level != null
				&& JianRong.currentScreen() == null) {
				mc.setScreenAndShow(new com.dnteam.linchi.gui.ShouCeScreen(null));
			}
			while (keyPeiZhi.consumeClick()) {
				if (mc.player == null) continue;
				try {
					Object cur = JianRong.currentScreen();
					// 正在搜索框打字时不响应开关，否则输入 P 会误关面板
					if (cur != null && JianRong.isTyping(cur)) continue;
					// 默认绑定 P：要求按住 CTRL；玩家改成别的键则单独按下即可。
					// 用反射读绑定键而不用 Fabric 的 getBoundKeyOf：部分 fabric-api
					// 版本缺该方法，运行期抛错会让 CTRL+P 分支整个失效。
					int bound = JianRong.boundKeyValue(keyPeiZhi);
					if (bound == InputConstants.KEY_P && !ctrlDown()) continue;
				// toggle 统一在这里做：面板自己的 keyPressed 不再管 P，避免关完
				// 同一帧又被 tick 重开。当前界面用反射取（26.1 字段 / 26.2+ 方法）。
					if (cur instanceof com.dnteam.linchi.gui.PeiZhiScreen)
						mc.setScreenAndShow(null);
					else
						mc.setScreenAndShow(new com.dnteam.linchi.gui.PeiZhiScreen(null));
				} catch (Throwable t) {
					LinChiMod.LOG.warn("CTRL+P 处理失败（不影响其他功能）", t);
				}
			}
			while (keyCaiLiao.consumeClick()) {
				if (mc.player == null) continue;
				Object cur = JianRong.currentScreen();
				if (cur != null && JianRong.isTyping(cur)) continue;
				if (cur instanceof com.dnteam.linchi.gui.CaiLiaoBanScreen)
					mc.setScreenAndShow(null);
				else
					mc.setScreenAndShow(new com.dnteam.linchi.gui.CaiLiaoBanScreen(null));
			}
		});

		com.dnteam.linchi.HudRender.register();
		com.dnteam.linchi.BanBen.register();
	}

	static boolean ctrlDown() {
		return JianRong.ctrlDown();
	}
}
