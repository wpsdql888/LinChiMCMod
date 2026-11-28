package com.dnteam.linchi;

import com.google.gson.*;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;

import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;

// 启动时检测一次模组和数据库版本；缓存只作断网兜底
public class BanBen {
	public static final String LOCAL = "1.2.1";
	// 数据库版本检测改走 ylm.one，旧地址 lins.dnteam.top/LinChi/data.json 留个底
	public static final String CHECK_URL = "https://ylm.one/index/download/mods/data.json";
	public static final String UPDATE_PAGE = "https://lins.dnteam.top/LinChi/update.html";
	// 发现新版本时聊天框里再补一条下载页链接，点了直接去模组下载站
	public static final String DOWNLOAD_PAGE = "https://ylm.one/download/mods/index";
	private static boolean checked = false;
	private static boolean dbChecked = false;
	// 本次启动是否已经成功联网拉过 data.json，避免主菜单和进世界各拉一次
	private static volatile boolean fresh = false;

	// 从 data.json 读出来的东西。db 相关字段没有就留 null
	public static class Remote {
		public final String version;
		public final String dbVersion;
		public final String dbUrl;

		public Remote(String version, String dbVersion, String dbUrl) {
			this.version = version;
			this.dbVersion = dbVersion;
			this.dbUrl = dbUrl;
		}
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			// 数据库检查进主菜单就跑，不必等进世界
			if (!dbChecked) {
				dbChecked = true;
				if (PeiZhi.INS.ziDongGengXinShuJu) checkData(mc);
			}
			if (checked || mc.player == null || mc.level == null) return;
			if (!PeiZhi.INS.jianCeBanBen && !PeiZhi.INS.ziDongGengXinShuJu) { checked = true; return; }
			checked = true;
			check(mc, true);
		});
	}

	// 只查数据库，主菜单也能跑；模组版本通知仍然等进世界
	public static void checkData(Minecraft mc) {
		Thread t = new Thread(() -> {
			Cache c = readCache();
			// 每次启动至少联网一次；失败才退回旧缓存
			if (!fresh || c == null) {
				Remote r = fetch();
				if (r != null) {
					writeCache(System.currentTimeMillis(), r);
					c = new Cache(System.currentTimeMillis(), r);
					fresh = true;
				}
			}
			if (c != null && c.remote != null && c.remote.dbVersion != null)
				ShuJuGengXin.apply(mc, c.remote);
		}, "linchi-db-check");
		t.setDaemon(true);
		t.start();
	}

	private static Path cacheFile() { return Paths.get("config", "linchi_update.json"); }

	// allowRequest=false 表示只拿缓存说话，不联网
	public static void check(Minecraft mc, boolean allowRequest) {
		Thread t = new Thread(() -> {
			Cache c = readCache();
			// 本次启动还没成功联网过就拉一次；主菜单已经拉过就直接用缓存
			if (allowRequest && (!fresh || c == null)) {
				Remote remote = fetch();
				if (remote != null) {
					writeCache(System.currentTimeMillis(), remote);
					c = new Cache(System.currentTimeMillis(), remote);
					fresh = true;
				}
			}
			if (c == null || c.remote == null) return;
			Remote r = c.remote;
			// 单人/多人都在启动后检查一次，别只在多人服务器里才提示
			if (PeiZhi.INS.jianCeBanBen && r.version != null && isOlder(LOCAL, r.version)) {
				String v = r.version;
				mc.execute(() -> {
					if (mc.player == null) return;
					Component link = Component.literal("点击此处")
						.withStyle(Style.EMPTY.withClickEvent(new ClickEvent.OpenUrl(URI.create(UPDATE_PAGE)))
							.withUnderlined(true));
					Component msg = Component.literal("[LinsMagic]有新版本v" + v + "可供使用，")
						.append(link).append("查看更新方式");
					mc.player.sendSystemMessage(msg);
					// 再补一条下载页链接，想直接拿 JAR 的不用去翻更新说明
					Component down = Component.literal("点击下载")
						.withStyle(Style.EMPTY.withClickEvent(new ClickEvent.OpenUrl(URI.create(DOWNLOAD_PAGE)))
							.withUnderlined(true));
					mc.player.sendSystemMessage(Component.literal("[LinsMagic] 新版 JAR ").append(down).append(" 前往下载页"));
				});
			}
			// 数据库更新不受多人游戏限制，单人存档也照常检查
			if (PeiZhi.INS.ziDongGengXinShuJu && r.dbVersion != null) {
				ShuJuGengXin.apply(mc, r);
			}
		}, "linchi-update-check");
		t.setDaemon(true);
		t.start();
	}

	private static Remote fetch() {
		try {
			HttpClient cli = HttpClient.newBuilder()
				.connectTimeout(Duration.ofSeconds(5))
				.followRedirects(HttpClient.Redirect.NORMAL)
				.build();
			HttpRequest req = HttpRequest.newBuilder(URI.create(CHECK_URL))
				.timeout(Duration.ofSeconds(10))
				.header("User-Agent", "LinChiMCMod/" + LOCAL).GET().build();
			HttpResponse<String> resp = cli.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
			if (resp.statusCode() != 200) return null;
			JsonObject o = JsonParser.parseString(ShuJuKu.stripBom(resp.body())).getAsJsonObject();
			String v = opt(o, "version");
			if (v == null) return null;
			String db = opt(o, "dbVersion");
			if (db == null) db = opt(o, "databaseVersion");
			String url = opt(o, "dbUrl");
			if (url == null) url = opt(o, "databaseUrl");
			return new Remote(clean(v), db == null ? null : clean(db), url);
		} catch (Exception e) {
			LinChiMod.LOG.debug("版本检测跳过: {}", e.getMessage());
			return null;
		}
	}

	// 手动检测：模组和数据库都查，每次都联网，不走 24h 缓存
	public static void manualCheck(Minecraft mc, java.util.function.Consumer<String> cb) {
		Thread t = new Thread(() -> {
			Remote r = fetch();
			String shortMsg;
			String chatMsg;
			// 手动检测也要带下载链接，标记一下这次是不是查到了新版本
			final boolean[] youNew = {false};
			if (r == null) {
				shortMsg = "模组检测失败\n数据库检测失败";
				chatMsg = "检测失败：无法获取 data.json";
			} else {
				String mod;
				if (r.version == null) mod = "模组版本未知";
				else if (isOlder(LOCAL, r.version)) { mod = "模组有新版本 v" + r.version; youNew[0] = true; }
				else mod = "模组已是最新版 v" + LOCAL;
				String db = r.dbVersion == null ? "数据库版本未提供" : ShuJuGengXin.apply(mc, r);
				shortMsg = mod + "\n" + db;
				chatMsg = mod + "；" + db;
			}
			String s = shortMsg, c = chatMsg;
			mc.execute(() -> {
				cb.accept(s);
				if (mc.player != null) {
					mc.player.sendSystemMessage(Component.literal("[LinChiMCMod] " + c));
					if (youNew[0]) {
						Component down = Component.literal("点击下载")
							.withStyle(Style.EMPTY.withClickEvent(new ClickEvent.OpenUrl(URI.create(DOWNLOAD_PAGE)))
								.withUnderlined(true));
						mc.player.sendSystemMessage(Component.literal("[LinChiMCMod] 新版 JAR ").append(down).append(" 前往下载页"));
					}
				}
			});
		}, "linchi-manual-check");
		t.setDaemon(true);
		t.start();
	}

	static String opt(JsonObject o, String k) {
		return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : null;
	}

	private static String clean(String v) {
		return v == null ? null : v.replace("v", "").trim();
	}

	private static class Cache {
		final long at;
		final Remote remote;

		Cache(long at, Remote remote) {
			this.at = at;
			this.remote = remote;
		}
	}

	private static Cache readCache() {
		try {
			Path p = cacheFile();
			if (!Files.exists(p)) return null;
			JsonObject o = JsonParser.parseString(ShuJuKu.stripBom(new String(Files.readAllBytes(p), StandardCharsets.UTF_8))).getAsJsonObject();
			String v = opt(o, "version");
			if (v == null) return null;
			return new Cache(o.get("at").getAsLong(),
				new Remote(v, opt(o, "dbVersion"), opt(o, "dbUrl")));
		} catch (Exception e) {
			return null;
		}
	}

	private static void writeCache(long at, Remote r) {
		try {
			Path p = cacheFile();
			Files.createDirectories(p.getParent());
			JsonObject o = new JsonObject();
			o.addProperty("at", at);
			o.addProperty("version", r.version);
			if (r.dbVersion != null) o.addProperty("dbVersion", r.dbVersion);
			if (r.dbUrl != null) o.addProperty("dbUrl", r.dbUrl);
			Files.write(p, o.toString().getBytes(StandardCharsets.UTF_8));
		} catch (Exception e) {
			LinChiMod.LOG.debug("版本缓存写不进去: {}", e.getMessage());
		}
	}

	static boolean isOlder(String local, String remote) {
		try {
			String[] a = local.split("\\."), b = remote.split("\\.");
			int n = Math.max(a.length, b.length);
			for (int i = 0; i < n; i++) {
				int x = i < a.length ? Integer.parseInt(a[i]) : 0;
				int y = i < b.length ? Integer.parseInt(b[i]) : 0;
				if (y > x) return true;
				if (y < x) return false;
			}
		} catch (Exception ignored) {}
		return false;
	}
}
