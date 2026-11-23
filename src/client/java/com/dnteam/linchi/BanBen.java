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
import java.util.regex.*;

// 启动时版本检测：拉 data.json 比版本号，旧了在聊天提示
// 请求很金贵 —— 结果落盘，24 小时内不再问服务器
public class BanBen {
	public static final String LOCAL = "1.1.1";
	public static final String CHECK_URL = "https://lins.dnteam.top/LinChi/data.json";
	public static final String UPDATE_PAGE = "https://lins.dnteam.top/LinChi/update.html";
	private static final long COOLDOWN = 24L * 60 * 60 * 1000;
	private static boolean checked = false;

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			if (checked || mc.player == null || mc.level == null) return;
			if (mc.getCurrentServer() == null) return; // 单人世界不检测
			if (!PeiZhi.INS.jianCeBanBen) { checked = true; return; }
			checked = true;
			check(mc, true);
		});
	}

	private static Path cacheFile() { return Paths.get("config", "linchi_update.json"); }

	// allowRequest=false 表示只拿缓存说话，不联网
	public static void check(Minecraft mc, boolean allowRequest) {
		Thread t = new Thread(() -> {
			Cache c = readCache();
			long age = c == null ? Long.MAX_VALUE : System.currentTimeMillis() - c.at;
			// age<0 说明缓存时间是未来（玩家改过系统时间），一律当作过期重新拉
			boolean needNet = age > COOLDOWN || age < 0;
			if (allowRequest && needNet) {
				String remote = fetch();
				if (remote != null) {
					writeCache(System.currentTimeMillis(), remote);
					c = new Cache(System.currentTimeMillis(), remote);
				}
			}
			if (c == null || c.version == null || !isOlder(LOCAL, c.version)) return;
			String v = c.version;
			mc.execute(() -> {
				if (mc.player == null) return;
				Component link = Component.literal("点击此处")
					.withStyle(Style.EMPTY.withClickEvent(new ClickEvent.OpenUrl(URI.create(UPDATE_PAGE)))
						.withUnderlined(true));
				Component msg = Component.literal("[LinsMagic]有新版本v" + v + "可供使用，")
					.append(link).append("查看更新方式");
				mc.player.sendSystemMessage(msg);
			});
		}, "linchi-update-check");
		t.setDaemon(true);
		t.start();
	}

	private static String fetch() {
		try {
			HttpClient cli = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
			HttpRequest req = HttpRequest.newBuilder(URI.create(CHECK_URL))
				.timeout(Duration.ofSeconds(6))
				.header("User-Agent", "LinChiMCMod/" + LOCAL).GET().build();
			HttpResponse<String> resp = cli.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
			if (resp.statusCode() != 200) return null;
			Matcher m = Pattern.compile("\"version\"\\s*:\\s*\"([^\"]+)\"").matcher(resp.body());
			if (!m.find()) return null;
			return m.group(1).replace("v", "").trim();
		} catch (Exception e) {
			LinChiMod.LOG.debug("版本检测跳过: {}", e.getMessage());
			return null;
		}
	}

	// 手动检测：每次都联网，不走 24h 缓存；结果同时回给界面和聊天栏
	public static void manualCheck(Minecraft mc, java.util.function.Consumer<String> cb) {
		Thread t = new Thread(() -> {
			String remote = fetch();
			String shortMsg;
			String chatMsg;
			if (remote == null) {
				shortMsg = "检测失败";
				chatMsg = "检测失败：无法获取 data.json";
			} else if (isOlder(LOCAL, remote)) {
				shortMsg = "发现新版本 v" + remote;
				chatMsg = "发现新版本 v" + remote + "，当前 v" + LOCAL + "，请前往更新页面";
			} else {
				shortMsg = "已是最新版 v" + LOCAL;
				chatMsg = "当前已是最新版本 v" + LOCAL;
			}
			String s = shortMsg, c = chatMsg;
			mc.execute(() -> {
				cb.accept(s);
				if (mc.player != null) mc.player.sendSystemMessage(Component.literal("[LinChiMCMod] " + c));
			});
		}, "linchi-manual-check");
		t.setDaemon(true);
		t.start();
	}

	private static class Cache { final long at; final String version; Cache(long a, String v) { at = a; version = v; } }	private static Cache readCache() {
		try {
			Path p = cacheFile();
			if (!Files.exists(p)) return null;
			JsonObject o = JsonParser.parseString(new String(Files.readAllBytes(p), StandardCharsets.UTF_8)).getAsJsonObject();
			return new Cache(o.get("at").getAsLong(), o.has("version") && !o.get("version").isJsonNull() ? o.get("version").getAsString() : null);
		} catch (Exception e) { return null; }
	}

	private static void writeCache(long at, String version) {
		try {
			Path p = cacheFile();
			Files.createDirectories(p.getParent());
			JsonObject o = new JsonObject();
			o.addProperty("at", at);
			o.addProperty("version", version);
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
