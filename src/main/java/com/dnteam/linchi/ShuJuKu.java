package com.dnteam.linchi;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

// 全部魔法物品的数据库，启动时读一次资源就常驻
public class ShuJuKu {
	public static final ShuJuKu INS = new ShuJuKu();

	public final List<WuPin> all = new ArrayList<>();
	public final Map<String, WuPin> byName = new HashMap<>();
	// 用途索引：某个材料被哪些成品用到。右键看用途时直接查，别每次全表扫
	public final Map<String, List<WuPin>> uses = new HashMap<>();
	// 搜索结果的小缓存，键是输入串。 sliders 拖来拖去不会重复跑 2160 条
	private final LinkedHashMap<String, List<WuPin>> souCache = new LinkedHashMap<>();
	private static final int SOU_CACHE_MAX = 24;
	private boolean loaded = false;

	public void load() {
		if (loaded) return;
		loaded = true;
		try (InputStream in = getClass().getResourceAsStream("/assets/linchi/linchi_items.json")) {
			if (in == null) { LinChiMod.LOG.error("找不到 linchi_items.json"); return; }
			String txt = new String(in.readAllBytes(), StandardCharsets.UTF_8);
			JsonObject root = JsonParser.parseString(txt).getAsJsonObject();
			JsonArray arr = root.getAsJsonArray("items");
			for (JsonElement el : arr) {
				JsonObject o = el.getAsJsonObject();
				WuPin wp = new WuPin();
				wp.ming = str(o, "n");
				wp.mingLower = wp.ming == null ? "" : wp.ming.toLowerCase(Locale.ROOT);
				wp.fenLei = str(o, "c");
				wp.base = str(o, "b");
				wp.huoQu = str(o, "o");
				if (o.has("r")) wp.gongFang = parsePf(o.getAsJsonObject("r"), false);
				if (o.has("m")) {
					PeiFang mf = new PeiFang();
					JsonArray slots = o.getAsJsonArray("m");
					for (JsonElement s : slots) mf.ing.add(s.isJsonNull() ? null : s.getAsString());
					mf.mirror = true;
					wp.moJing = mf;
				}
				all.add(wp);
				if (wp.ming != null) byName.put(wp.ming, wp);
			}
			buildUses();
			LinChiMod.LOG.info("物品库加载: {} 条", all.size());
		} catch (Exception e) {
			LinChiMod.LOG.error("物品库加载失败", e);
		}
	}

	PeiFang parsePf(JsonObject o, boolean mirror) {
		PeiFang p = new PeiFang();
		if (o.has("count")) p.count = o.get("count").getAsInt();
		if (o.has("shape")) for (JsonElement s : o.getAsJsonArray("shape")) p.shape.add(s.getAsString());
		if (o.has("mat")) {
			JsonObject mm = o.getAsJsonObject("mat");
			for (Map.Entry<String, JsonElement> en : mm.entrySet()) p.mat.put(en.getKey(), en.getValue().getAsString());
		}
		if (o.has("shapeless")) p.shapeless = o.get("shapeless").getAsBoolean();
		if (o.has("ing")) for (JsonElement s : o.getAsJsonArray("ing")) p.ing.add(s.isJsonNull() ? null : s.getAsString());
		p.mirror = mirror;
		return p;
	}

	static String str(JsonObject o, String k) {
		return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : null;
	}

	public WuPin get(String ming) { return ming == null ? null : byName.get(ming); }

	// 用材料字符串拿图标：minecraft:xxx 直接用，中文名查 base
	public String baseOf(String key) {
		if (key == null) return null;
		if (key.startsWith("minecraft:")) return key.substring(10);
		WuPin wp = byName.get(key);
		return wp == null ? null : wp.base;
	}

	// 把每个材料反向登记到“谁用到了它”。右键看用途就靠这张表
	private void buildUses() {
		for (WuPin wp : all) {
			LinkedHashSet<String> keys = new LinkedHashSet<>();
			if (wp.gongFang != null) {
				for (String k : wp.gongFang.jiuGe()) if (k != null) keys.add(k);
				for (String k : wp.gongFang.mat.values()) if (k != null) keys.add(k);
			}
			if (wp.moJing != null) for (String k : wp.moJing.ing) if (k != null) keys.add(k);
			for (String k : keys) {
				uses.computeIfAbsent(k, x -> new ArrayList<>()).add(wp);
			}
		}
	}

	public List<WuPin> getUses(String key) {
		if (key == null) return Collections.emptyList();
		List<WuPin> r = uses.get(key);
		return r == null ? Collections.emptyList() : r;
	}

	// 供搜索：按名字/分类模糊过滤。结果带小缓存，重复输入不重算
	public List<WuPin> sou(String kw) {
		if (kw == null || kw.isBlank()) return all;
		String q = kw.trim().toLowerCase(Locale.ROOT);
		List<WuPin> hit = souCache.get(q);
		if (hit != null) return hit;
		ArrayList<WuPin> out = new ArrayList<>();
		for (WuPin wp : all) {
			if (wp.mingLower.contains(q) || (wp.fenLei != null && wp.fenLei.toLowerCase(Locale.ROOT).contains(q))) out.add(wp);
		}
		out.trimToSize();
		souCache.put(q, out);
		if (souCache.size() > SOU_CACHE_MAX) {
			Iterator<String> it = souCache.keySet().iterator();
			it.next();
			it.remove();
		}
		return out;
	}
}
