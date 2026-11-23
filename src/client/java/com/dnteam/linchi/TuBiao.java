package com.dnteam.linchi;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Map;

// 图标工具：base / 材料字符串 -> 可渲染的 ItemStack
// 每帧要画几十个图标，每次都 new 一个栈太浪费，这里做个常驻缓存
public class TuBiao {
	private static final Map<String, ItemStack> CACHE = new HashMap<>();
	// 原始 key 直接映射结果：命中后连 baseOf 查询、小写化、字符串拼接全省掉。
	// 渲染一帧要画几十个图标，这一层能砍掉绝大部分重复解析。
	private static final Map<String, ItemStack> OF_CACHE = new HashMap<>();

	public static ItemStack of(String key) {
		if (key == null) return ItemStack.EMPTY;
		// 只有魔法物品才有附魔光效。原版材料（minecraft:xxx）就让它素着，
		// 之前无脑给所有图标加光，连铁锭都在闪。
		boolean moFa = !key.startsWith("minecraft:");
		boolean guang = PeiZhi.INS.fuMoGuang && moFa;
		String ck = key + "|" + (guang ? 1 : 0);
		ItemStack hit = OF_CACHE.get(ck);
		if (hit != null) return hit;
		String base = ShuJuKu.INS.baseOf(key);
		// baseOf 只认 "minecraft:xxx" 和魔法物品中文名；
		// 魔法物品的 base 字段是裸 id（如 "goat_horn"），baseOf 会返回 null，
		// 这里兜底把 key 本身当原版 id 再试一次，否则列表/HUD 里魔法物品永远没图标
		ItemStack st = fromBase(base == null ? key : base, guang);
		OF_CACHE.put(ck, st);
		return st;
	}

	// 旧调用：默认不给光效
	public static ItemStack fromBase(String base) { return fromBase(base, false); }

	public static ItemStack fromBase(String base, boolean guang) {
		if (base == null || base.isEmpty()) return ItemStack.EMPTY;
		String id = base.contains(":") ? base.toLowerCase(java.util.Locale.ROOT) : "minecraft:" + base.toLowerCase(java.util.Locale.ROOT);
		String cacheKey = id + "|" + (guang ? 1 : 0);
		ItemStack cached = CACHE.get(cacheKey);
		if (cached != null) return cached;
		try {
			Item it = BuiltInRegistries.ITEM.getOptional(Identifier.parse(id)).orElse(null);
			if (it == null) {
				CACHE.put(cacheKey, ItemStack.EMPTY);
				return ItemStack.EMPTY;
			}
			ItemStack st = new ItemStack(it);
			if (guang) {
				try { st.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true); } catch (Throwable ignored) {}
			}
			CACHE.put(cacheKey, st);
			return st;
		} catch (Exception e) {
			CACHE.put(cacheKey, ItemStack.EMPTY);
			return ItemStack.EMPTY;
		}
	}

	// 材料 key -> 显示名。魔法物品直接返回库里的中文名；
	// 原版 id 走客户端翻译（minecraft:iron_ingot -> 铁锭）。
	// 判定和提示都认这个，别再看 NBT / 注册名。
	private static final Map<String, String> MING_CACHE = new HashMap<>();

	public static String mingOf(String key) {
		if (key == null || key.isEmpty()) return "";
		String hit = MING_CACHE.get(key);
		if (hit != null) return hit;
		WuPin wp = ShuJuKu.INS.get(key);
		String n;
		if (wp != null && wp.ming != null) {
			n = wp.ming;
		} else {
			ItemStack st = of(key);
			n = st.isEmpty() ? key : st.getHoverName().getString();
		}
		MING_CACHE.put(key, n);
		return n;
	}

	// 光效开关变了就把缓存丢掉，不然图标会停留在旧状态
	public static void clearCache() { CACHE.clear(); OF_CACHE.clear(); }
}
