package com.dnteam.linchi;

import com.google.gson.*;
import net.minecraft.client.Minecraft;

import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;

// 数据库 OTA：物品配方数据单独更新
// 版本号存在 config/linchi_data_version.json，数据本体在 config/linchi_data.json
public class ShuJuGengXin {
	private static final String DEFAULT_VERSION = "1.0.0";
	private static final String DEFAULT_URL = "https://lins.dnteam.top/LinChi/linchi_items.json";

	public static Path versionFile() {
		return Paths.get("config", "linchi_data_version.json");
	}

	// 客户端启动时调一次：本地没有版本文件就建一个 1.0.0
	public static void init() {
		localVersion();
	}

	public static String localVersion() {
		JsonObject o = readVersion();
		String v = o == null ? null : BanBen.opt(o, "version");
		if (v == null || v.isBlank()) {
			writeVersion(DEFAULT_VERSION, "");
			return DEFAULT_VERSION;
		}
		return v;
	}

	// 返回给界面和聊天栏的一句话
	public static String apply(Minecraft mc, BanBen.Remote remote) {
		String local = localVersion();
		String remoteVer = remote.dbVersion;
		if (remoteVer == null || remoteVer.isBlank()) return "数据库版本未提供";

		Path data = ShuJuKu.dataPath();
		String hash = localHash();
		boolean hasData = Files.exists(data);
		boolean broken = hasData && !validFile(data);
		// 以前下过（有哈希）但现在文件没了，也算缺档
		boolean missing = !hasData && !hash.isEmpty();
		boolean newer = BanBen.isOlder(local, remoteVer);
		boolean changed = false;
		if (hasData && !broken && !hash.isEmpty()) {
			try {
				changed = !hash.equals(ShuJuKu.sha256(Files.readString(data, StandardCharsets.UTF_8)));
			} catch (Exception ignored) {}
		}
		if (!broken && !missing && !changed && !newer) return "数据库已是最新版 " + local;

		String url = remote.dbUrl == null || remote.dbUrl.isBlank() ? DEFAULT_URL : resolve(remote.dbUrl);
		String body = download(url);
		if (body == null) {
			return (broken || missing || changed)
				? "本地数据库需要重新下载，但下载失败"
				: "发现数据库 v" + remoteVer + "，但下载失败";
		}
		if (!ShuJuKu.validData(body)) {
			String head = body.substring(0, Math.min(80, body.length())).replaceAll("\\s+", " ");
			LinChiMod.LOG.warn("数据库校验失败: 长度={} 开头={}", body.length(), head);
			return (broken || missing || changed)
				? "本地数据库需要重新下载，但内容校验失败"
				: "发现数据库 v" + remoteVer + "，但内容校验失败";
		}
		if (!save(body)) return "数据库保存失败";
		writeVersion(remoteVer, ShuJuKu.sha256(body));
		mc.execute(() -> {
			ShuJuKu.INS.reload();
			TuBiao.clearCache();
			ZhuangTai.INS.recalc();
		});
		if (broken) return "本地数据库已修复，更新至 " + remoteVer;
		if (missing) return "本地数据库缺失，已重新下载至 " + remoteVer;
		if (changed) return "数据库校验不符，已重新同步至 " + remoteVer;
		return "数据库已更新至 " + remoteVer;
	}

	private static JsonObject readVersion() {
		try {
			Path p = versionFile();
			if (!Files.exists(p)) return null;
			String txt = ShuJuKu.stripBom(Files.readString(p, StandardCharsets.UTF_8));
			return JsonParser.parseString(txt).getAsJsonObject();
		} catch (Exception e) {
			return null;
		}
	}

	private static String localHash() {
		JsonObject o = readVersion();
		String h = o == null ? null : BanBen.opt(o, "hash");
		return h == null ? "" : h;
	}

	private static void writeVersion(String version, String hash) {
		try {
			Path p = versionFile();
			Files.createDirectories(p.getParent());
			JsonObject o = new JsonObject();
			o.addProperty("version", version);
			o.addProperty("hash", hash);
			Files.writeString(p, o.toString(), StandardCharsets.UTF_8);
		} catch (Exception e) {
			LinChiMod.LOG.warn("数据库版本文件写入失败", e);
		}
	}

	private static boolean validFile(Path p) {
		try {
			return ShuJuKu.validData(Files.readString(p, StandardCharsets.UTF_8));
		} catch (Exception e) {
			return false;
		}
	}

	// data.json 里的 dbUrl 可以写相对路径
	private static String resolve(String url) {
		try {
			if (url.startsWith("http://") || url.startsWith("https://")) return url;
			return URI.create(BanBen.CHECK_URL).resolve(url).toString();
		} catch (Exception e) {
			return DEFAULT_URL;
		}
	}

	private static String download(String url) {
		try {
			HttpClient cli = HttpClient.newBuilder()
				.connectTimeout(Duration.ofSeconds(8))
				.followRedirects(HttpClient.Redirect.NORMAL)
				.build();
			HttpRequest req = HttpRequest.newBuilder(URI.create(url))
				.timeout(Duration.ofSeconds(30))
				.header("User-Agent", "LinChiMCMod/" + BanBen.LOCAL)
				.header("Accept-Encoding", "identity")
				.GET().build();
			HttpResponse<String> resp = cli.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
			if (resp.statusCode() != 200) {
				LinChiMod.LOG.warn("数据库下载 HTTP {}: {}", resp.statusCode(), url);
				return null;
			}
			String body = resp.body();
			if (body == null || body.isEmpty()) {
				LinChiMod.LOG.warn("数据库下载为空: {}", url);
				return null;
			}
			return body;
		} catch (Exception e) {
			LinChiMod.LOG.warn("数据库下载失败 {}: {}", url, e.toString());
			return null;
		}
	}

	private static boolean save(String body) {
		try {
			Path p = ShuJuKu.dataPath();
			Files.createDirectories(p.getParent());
			Path tmp = p.resolveSibling(p.getFileName() + ".tmp");
			Files.writeString(tmp, body, StandardCharsets.UTF_8);
			try {
				Files.move(tmp, p, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			} catch (Exception e) {
				Files.move(tmp, p, StandardCopyOption.REPLACE_EXISTING);
			}
			return true;
		} catch (Exception e) {
			LinChiMod.LOG.warn("数据库保存失败", e);
			return false;
		}
	}
}
