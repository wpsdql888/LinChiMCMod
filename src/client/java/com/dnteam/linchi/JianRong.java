package com.dnteam.linchi;

import net.minecraft.client.Minecraft;
import java.lang.reflect.*;

// 兼容 26.1/26.2/26.3 之间的 API 差异（全程反射，不绑死任何签名）
public class JianRong {

	// 返回 Minecraft 的 Window 对象本身（不是 long 句柄）。
	// 旧代码在这里找 "getWindow()" 返回 long 的方法，但各版本的 Window 类里
	// 这个方法其实叫 handle()，导致永远返回 null，26.1/26.2 里需要 Window
	// 参数的 setClipboard / isKeyDown 全部静默失败。
	static Object windowObject() {
		try {
			return Minecraft.getInstance().getWindow();
		} catch (Throwable ignored) {
			return null;
		}
	}

	// 剪贴板：26.3 是 setClipboard(String)，旧版是 setClipboard(Window,String)
	public static void copy(String txt) {
		try {
			Class<?> cm = Class.forName("com.mojang.blaze3d.platform.ClipboardManager");
			Object inst = cm.getDeclaredConstructor().newInstance();
			Object win = windowObject();
			for (Method m : cm.getMethods()) {
				if (!m.getName().equals("setClipboard")) continue;
				Class<?>[] p = m.getParameterTypes();
				try {
					if (p.length == 1 && p[0] == String.class) { m.invoke(inst, txt); return; }
					// 两个参数：(Window, String)。按参数类型核对，避免误调用别的重载。
					if (p.length == 2 && p[1] == String.class && win != null && p[0].isInstance(win)) {
						m.invoke(inst, win, txt); return;
					}
				} catch (Throwable ignored) {}
			}
		} catch (Throwable e) {
			LinChiMod.LOG.debug("复制失败 {}", e.getMessage());
		}
	}

	// 当前界面：26.1 是 Minecraft.screen 字段，26.2+ 是 Minecraft.gui.screen() 方法
	public static Object currentScreen() {
		try {
			Object mc = Minecraft.getInstance();
			// 26.2+ ：mc.gui.screen()
			try {
				Object gui = mc.getClass().getField("gui").get(mc);
				Method sm = gui.getClass().getMethod("screen");
				return sm.invoke(gui);
			} catch (Throwable ignored) {}
			// 26.1 ：mc.screen 字段
			try {
				return mc.getClass().getField("screen").get(mc);
			} catch (Throwable ignored) {}
		} catch (Throwable ignored) {}
		return null;
	}

	// 当前界面里是否有 EditBox 正在获得焦点（正在打字）。
	// 快捷键开关面板时要跳过打字状态，否则在搜索框输入 N/P 会误触开关。
	public static boolean isTyping(Object screen) {
		if (screen == null) return false;
		try {
			Object children = screen.getClass().getMethod("children").invoke(screen);
			if (children instanceof java.util.List<?>) {
				for (Object c : (java.util.List<?>) children) {
					if (c == null) continue;
					try {
						if (c.getClass().getName().endsWith("EditBox")
							&& Boolean.TRUE.equals(c.getClass().getMethod("isFocused").invoke(c))) return true;
					} catch (Throwable ignored) {}
				}
			}
		} catch (Throwable ignored) {}
		return false;
	}

	// CTRL 是否按下
	public static boolean ctrlDown() {
		try {
			Class<?> ic = Class.forName("com.mojang.blaze3d.platform.InputConstants");
			int lc = ic.getField("KEY_LCONTROL").getInt(null);
			int rc = ic.getField("KEY_RCONTROL").getInt(null);
			Object win = windowObject();
			for (Method m : ic.getMethods()) {
				if (!m.getName().equals("isKeyDown")) continue;
				Class<?>[] p = m.getParameterTypes();
				try {
					if (p.length == 1 && p[0] == int.class) {
						if ((boolean)m.invoke(null, lc) || (boolean)m.invoke(null, rc)) return true;
					} else if (p.length == 2 && p[1] == int.class && win != null && p[0].isInstance(win)) {
						if ((boolean)m.invoke(null, win, lc) || (boolean)m.invoke(null, win, rc)) return true;
					}
				} catch (Throwable ignored) {}
			}
		} catch (Throwable ignored) {}
		return false;
	}
}
