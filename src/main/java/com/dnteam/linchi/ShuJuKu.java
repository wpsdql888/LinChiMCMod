package com.dnteam.linchi;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

// 全部魔法物品的数据库，启动时读一次就常驻
// config/linchi_data.json 存在且没坏就用它，否则回退到 jar 里的那份
public class ShuJuKu {
	public static final ShuJuKu INS = new ShuJuKu();
	private static final String BUILTIN = "/assets/linchi/linchi_items.json";

	public final List<WuPin> all = new ArrayList<>();
	public final Map<String, WuPin> byName = new HashMap<>();
	// 用途索引：某个材料被哪些成品用到。右键看用途时直接查，别每次全表扫
	public final Map<String, List<WuPin>> uses = new HashMap<>();
	// 搜索结果的小缓存，键是输入串。 sliders 拖来拖去不会重复跑 2160 条
	private final LinkedHashMap<String, List<WuPin>> souCache = new LinkedHashMap<>();
	private static final int SOU_CACHE_MAX = 24;
	private boolean loaded = false;

	// OTA 下载下来的数据库放这，版本号另存在 linchi_data_version.json
	public static Path dataPath() {
		return Paths.get("config", "linchi_data.json");
	}

	public void load() {
		if (loaded) return;
		loaded = true;
		String txt = readText(dataPath());
		if (txt != null) {
			if (parse(txt)) {
				LinChiMod.LOG.info("物品库加载(本地): {} 条", all.size());
				return;
			}
			LinChiMod.LOG.warn("本地数据库残缺，回退内置数据");
		}
		try (InputStream in = getClass().getResourceAsStream(BUILTIN)) {
			if (in == null) { LinChiMod.LOG.error("找不到 linchi_items.json"); return; }
			if (!parse(new String(in.readAllBytes(), StandardCharsets.UTF_8)))
				LinChiMod.LOG.error("内置物品库解析失败");
			else
				LinChiMod.LOG.info("物品库加载(内置): {} 条", all.size());
		} catch (Exception e) {
			LinChiMod.LOG.error("物品库加载失败", e);
		}
	}

	// 换库后清掉索引和缓存，重新读
	public void reload() {
		all.clear();
		byName.clear();
		uses.clear();
		souCache.clear();
		loaded = false;
		load();
	}

	private boolean parse(String txt) {
		try {
			JsonObject root = JsonParser.parseString(stripBom(txt)).getAsJsonObject();
			JsonArray arr = root.getAsJsonArray("items");
			if (arr == null || arr.isEmpty()) return false;
			List<WuPin> list = new ArrayList<>(arr.size());
			Map<String, WuPin> map = new HashMap<>();
			for (JsonElement el : arr) {
				JsonObject o = el.getAsJsonObject();
				WuPin wp = new WuPin();
				// 兼容两种数据：打包用的短键 n/c/b/o，服务器上的原始 name/category/base/obtain
				wp.ming = first(o, "n", "name");
				if (wp.ming == null || wp.ming.isBlank()) return false;
				wp.mingLower = wp.ming.toLowerCase(Locale.ROOT);
				wp.fenLei = first(o, "c", "category");
				wp.fenLeiLower = wp.fenLei == null ? "" : wp.fenLei.toLowerCase(Locale.ROOT);
				wp.base = baseOfRaw(first(o, "b", "base"));
				wp.huoQu = first(o, "o", "obtain");
				JsonElement rEl = o.has("r") ? o.get("r") : o.get("recipe");
				if (rEl != null && rEl.isJsonObject()) wp.gongFang = parsePf(rEl.getAsJsonObject(), false);
				JsonElement mEl = o.has("m") ? o.get("m") : o.get("mirrorRecipe");
				if (mEl != null && !mEl.isJsonNull()) wp.moJing = parseMirror(mEl);
				JsonElement uEl = o.get("usedIn");
				if (uEl != null && uEl.isJsonArray()) {
					List<String> used = new ArrayList<>();
					for (JsonElement u : uEl.getAsJsonArray())
						if (!u.isJsonNull()) used.add(u.getAsString());
					wp.usedIn = used;
				}
				list.add(wp);
				map.put(wp.ming, wp);
			}
			all.clear();
			all.addAll(list);
			byName.clear();
			byName.putAll(map);
			buildUses();
			return true;
		} catch (Exception e) {
			return false;
		}
	}

	PeiFang parsePf(JsonObject o, boolean mirror) {
		PeiFang p = new PeiFang();
		if (o.has("count")) p.count = o.get("count").getAsInt();
		if (o.has("shape")) for (JsonElement s : o.getAsJsonArray("shape")) p.shape.add(s.getAsString());
		JsonObject mm = null;
		if (o.has("mat")) mm = o.getAsJsonObject("mat");
		else if (o.has("materials")) mm = o.getAsJsonObject("materials");
		if (mm != null) {
			for (Map.Entry<String, JsonElement> en : mm.entrySet())
				p.mat.put(en.getKey(), en.getValue().isJsonNull() ? null : en.getValue().getAsString());
		}
		if (o.has("shapeless")) p.shapeless = o.get("shapeless").getAsBoolean();
		JsonArray ing = null;
		if (o.has("ing")) ing = o.getAsJsonArray("ing");
		else if (o.has("shapelessIngredients")) ing = o.getAsJsonArray("shapelessIngredients");
		if (ing != null) for (JsonElement s : ing) p.ing.add(s.isJsonNull() ? null : s.getAsString());
		p.mirror = mirror;
		return p;
	}

	// 魔镜配方：打包数据是 9 个字符串，服务器原始数据是 {kind, slots:[{name,base}]}
	PeiFang parseMirror(JsonElement el) {
		PeiFang mf = new PeiFang();
		mf.mirror = true;
		if (el.isJsonArray()) {
			for (JsonElement s : el.getAsJsonArray()) mf.ing.add(s.isJsonNull() ? null : s.getAsString());
		} else if (el.isJsonObject()) {
			JsonArray slots = el.getAsJsonObject().getAsJsonArray("slots");
			if (slots != null) for (JsonElement s : slots) {
				if (s.isJsonNull()) { mf.ing.add(null); continue; }
				if (s.isJsonPrimitive()) { mf.ing.add(s.getAsString()); continue; }
				JsonObject so = s.getAsJsonObject();
				String k = first(so, "name", "base");
				// 原始数据里只有 base 时是 NETHERITE_INGOT 这种枚举名，补上命名空间
				if (k != null && k.matches("[A-Z0-9_]+")) k = "minecraft:" + k.toLowerCase(Locale.ROOT);
				mf.ing.add(k);
			}
		}
		return mf.ing.isEmpty() ? null : mf;
	}

	static String first(JsonObject o, String a, String b) {
		String v = str(o, a);
		return v != null ? v : str(o, b);
	}

	// 原始 base 是 GOAT_HORN，打包数据是 goat_horn；统一成裸小写 id
	static String baseOfRaw(String base) {
		if (base == null) return null;
		base = base.trim().toLowerCase(Locale.ROOT);
		return base.startsWith("minecraft:") ? base.substring(10) : base;
	}

	public static String stripBom(String s) {
		return s != null && s.startsWith("\uFEFF") ? s.substring(1) : s;
	}

	// 显示名归一化：服务器插件常在名字里塞颜色码、私有区图标、零宽字符，
	// 直接 equals 会漏掉一部分物品。统计和填入都走这里。
	public static String mingKey(String s) {
		if (s == null) return "";
		StringBuilder sb = new StringBuilder(s.length());
		for (int i = 0; i < s.length(); ) {
			int cp = s.codePointAt(i);
			i += Character.charCount(cp);
			if (cp == 0x00A7) { // § 后跟一个格式码，整对丢掉
				if (i < s.length()) i += Character.charCount(s.codePointAt(i));
				continue;
			}
			int t = Character.getType(cp);
			if (t == Character.CONTROL || t == Character.FORMAT || t == Character.PRIVATE_USE
				|| t == Character.UNASSIGNED || Character.isWhitespace(cp) || Character.isSpaceChar(cp)) continue;
			sb.appendCodePoint(cp);
		}
		String out = sb.toString();
		try {
			out = java.text.Normalizer.normalize(out, java.text.Normalizer.Form.NFKC);
		} catch (Exception ignored) {}
		// 两头常见的装饰括号/星号也去掉，别让“[绑定] 烈焰结晶”对不上
		int a = 0, b = out.length();
		String deco = "[]【】()（）<>《》*·-—_";
		while (a < b && deco.indexOf(out.charAt(a)) >= 0) a++;
		while (b > a && deco.indexOf(out.charAt(b - 1)) >= 0) b--;
		return out.substring(a, b).toLowerCase(Locale.ROOT);
	}

	static String str(JsonObject o, String k) {
		return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : null;
	}

	private static String readText(Path p) {
		try {
			if (!Files.exists(p)) return null;
			return Files.readString(p, StandardCharsets.UTF_8);
		} catch (Exception e) {
			return null;
		}
	}

	// 下载回来的内容先过这一关：能解析、有 items、每条都有名字才算完整
	public static boolean validData(String txt) {
		try {
			JsonObject root = JsonParser.parseString(stripBom(txt)).getAsJsonObject();
			JsonArray arr = root.getAsJsonArray("items");
			if (arr == null || arr.isEmpty()) return false;
			for (JsonElement el : arr) {
				JsonObject o = el.getAsJsonObject();
				String n = first(o, "n", "name");
				if (n == null || n.isBlank()) return false;
			}
			return true;
		} catch (Exception e) {
			return false;
		}
	}

	public static String sha256(String txt) {
		try {
			byte[] d = MessageDigest.getInstance("SHA-256").digest(txt.getBytes(StandardCharsets.UTF_8));
			StringBuilder sb = new StringBuilder(d.length * 2);
			for (byte b : d) {
				sb.append(Character.forDigit((b >> 4) & 0xF, 16));
				sb.append(Character.forDigit(b & 0xF, 16));
			}
			return sb.toString();
		} catch (Exception e) {
			return "";
		}
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
		uses.clear();
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
			// 原始数据自带 usedIn，装配台这类没有工作台配方的用途靠它补
			if (wp.usedIn != null) {
				for (String prod : wp.usedIn) {
					WuPin p = byName.get(prod);
					if (p == null || p == wp) continue;
					List<WuPin> list = uses.computeIfAbsent(wp.ming, x -> new ArrayList<>());
					if (!list.contains(p)) list.add(p);
				}
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
			if (wp.mingLower.contains(q) || wp.fenLeiLower.contains(q)) out.add(wp);
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
