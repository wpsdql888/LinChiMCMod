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

	public static ItemStack of(String key) {
		if (key == null) return ItemStack.EMPTY;
		String base = ShuJuKu.INS.baseOf(key);
		// baseOf 只认 "minecraft:xxx" 和魔法物品中文名；
		// 魔法物品的 base 字段是裸 id（如 "goat_horn"），baseOf 会返回 null，
		// 这里兜底把 key 本身当原版 id 再试一次，否则列表/HUD 里魔法物品永远没图标
		if (base == null) return fromBase(key);
		return fromBase(base);
	}

	public static ItemStack fromBase(String base) {
		if (base == null || base.isEmpty()) return ItemStack.EMPTY;
		String id = base.contains(":") ? base.toLowerCase(java.util.Locale.ROOT) : "minecraft:" + base.toLowerCase(java.util.Locale.ROOT);
		String cacheKey = id + "|" + (PeiZhi.INS.fuMoGuang ? 1 : 0);
		ItemStack cached = CACHE.get(cacheKey);
		if (cached != null) return cached;
		try {
			Item it = BuiltInRegistries.ITEM.getOptional(Identifier.parse(id)).orElse(null);
			if (it == null) {
				CACHE.put(cacheKey, ItemStack.EMPTY);
				return ItemStack.EMPTY;
			}
			ItemStack st = new ItemStack(it);
			if (PeiZhi.INS.fuMoGuang) {
				try { st.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true); } catch (Throwable ignored) {}
			}
			CACHE.put(cacheKey, st);
			return st;
		} catch (Exception e) {
			CACHE.put(cacheKey, ItemStack.EMPTY);
			return ItemStack.EMPTY;
		}
	}

	// 光效开关变了就把缓存丢掉，不然图标会停留在旧状态
	public static void clearCache() { CACHE.clear(); }
}
