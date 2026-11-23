package com.dnteam.linchi;

import net.minecraft.client.Minecraft;
import java.lang.reflect.*;

// 兼容 26.1/26.2/26.3 之间的 API 差异（全程反射，不绑死任何签名）。
// 反射查找（Class.forName / getMethod / getField）只做一次并缓存 Method、Field 对象，
// 因为 currentScreen() 每 tick 会调多次、ctrlDown() 每次按键都调 —— 每次都遍历
// getMethods() 是实打实的持续开销。
public class JianRong {

	// ---- 缓存的反射成员（延迟初始化；取不到就保持 null 并每次重试，不缓存失败）----
	private static Method M_ISKEYDOWN_1, M_ISKEYDOWN_2;
	private static Field F_LCONTROL, F_RCONTROL;
	private static Field F_MC_GUI, F_MC_SCREEN;
	private static Method M_GUI_SCREEN;
	private static boolean GUI_SCREEN_TRIED; // 26.1 没有 gui.screen()，别每帧重试失败的查找
	private static Field F_KM_KEY, F_KM_CLICKCOUNT;
	private static Method M_KEYVALUE;

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
			if (F_MC_GUI == null) F_MC_GUI = findField(mc.getClass(), "gui");
			Object gui = F_MC_GUI == null ? null : F_MC_GUI.get(mc);
			if (gui != null) {
				if (M_GUI_SCREEN == null && !GUI_SCREEN_TRIED) {
					GUI_SCREEN_TRIED = true;
					try { M_GUI_SCREEN = gui.getClass().getMethod("screen"); }
					catch (Throwable ignored) {}
				}
				if (M_GUI_SCREEN != null) return M_GUI_SCREEN.invoke(gui);
			}
			// 26.1 ：mc.screen 字段
			if (F_MC_SCREEN == null) F_MC_SCREEN = findField(mc.getClass(), "screen");
			if (F_MC_SCREEN != null) return F_MC_SCREEN.get(mc);
		} catch (Throwable ignored) {}
		return null;
	}

	// 按名字找字段（public 直接读，private 就强开），找不到返回 null
	private static Field findField(Class<?> cls, String name) {
		try {
			Field f = cls.getField(name);
			f.setAccessible(true);
			return f;
		} catch (Throwable ignored) {}
		try {
			Field f = cls.getDeclaredField(name);
			f.setAccessible(true);
			return f;
		} catch (Throwable ignored) {}
		return null;
	}

	// 读 KeyMapping 当前绑定的键值。不用 Fabric 的 KeyMappingHelper.getBoundOf：
	// 部分 fabric-api 版本没有这个方法，运行期直接抛错会让 CTRL+P 整个失效
	// （N 键分支不调用它，所以表现成“N 正常、CTRL+P 完全没反应”）。
	public static int boundKeyValue(net.minecraft.client.KeyMapping km) {
		if (km == null) return -1;
		try {
			if (F_KM_KEY == null) F_KM_KEY = findField(net.minecraft.client.KeyMapping.class, "key");
			Object k = F_KM_KEY == null ? null : F_KM_KEY.get(km);
			if (k != null) {
				if (M_KEYVALUE == null) {
					try { M_KEYVALUE = k.getClass().getMethod("getValue"); }
					catch (Throwable ignored) {}
				}
				if (M_KEYVALUE != null) return (int) M_KEYVALUE.invoke(k);
			}
		} catch (Throwable ignored) {}
		try { return km.getDefaultKey().getValue(); } catch (Throwable ignored) {}
		return -1;
	}

	// 把按键的点击计数清零（压制原版按键消费时配套用，否则计数会积压到松键后补触发）
	public static void resetClickCount(net.minecraft.client.KeyMapping km) {
		if (km == null) return;
		try {
			if (F_KM_CLICKCOUNT == null)
				F_KM_CLICKCOUNT = findField(net.minecraft.client.KeyMapping.class, "clickCount");
			if (F_KM_CLICKCOUNT != null) F_KM_CLICKCOUNT.setInt(km, 0);
		} catch (Throwable ignored) {}
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

	// CTRL 是否按下。isKeyDown 在 getMethods() 里只找一次并缓存。
	public static boolean ctrlDown() {
		try {
			if (F_LCONTROL == null || F_RCONTROL == null) {
				Class<?> ic = Class.forName("com.mojang.blaze3d.platform.InputConstants");
				F_LCONTROL = ic.getField("KEY_LCONTROL");
				F_RCONTROL = ic.getField("KEY_RCONTROL");
			}
			int lc = F_LCONTROL.getInt(null), rc = F_RCONTROL.getInt(null);
			if (M_ISKEYDOWN_1 == null && M_ISKEYDOWN_2 == null) {
				Class<?> ic = Class.forName("com.mojang.blaze3d.platform.InputConstants");
				for (Method m : ic.getMethods()) {
					if (!m.getName().equals("isKeyDown")) continue;
					Class<?>[] p = m.getParameterTypes();
					if (p.length == 1 && p[0] == int.class) M_ISKEYDOWN_1 = m;
					else if (p.length == 2 && p[1] == int.class
						&& p[0].getName().endsWith("Window")) M_ISKEYDOWN_2 = m;
				}
			}
			Object win = windowObject();
			if (M_ISKEYDOWN_1 != null)
				return (boolean) M_ISKEYDOWN_1.invoke(null, lc) || (boolean) M_ISKEYDOWN_1.invoke(null, rc);
			if (M_ISKEYDOWN_2 != null && win != null && M_ISKEYDOWN_2.getParameterTypes()[0].isInstance(win))
				return (boolean) M_ISKEYDOWN_2.invoke(null, win, lc) || (boolean) M_ISKEYDOWN_2.invoke(null, win, rc);
		} catch (Throwable ignored) {}
		return false;
	}
}
