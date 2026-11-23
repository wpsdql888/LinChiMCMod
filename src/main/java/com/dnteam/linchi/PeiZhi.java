package com.dnteam.linchi;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

// 配置：全局设置存 config/linchi.json。样式参数都在这
public class PeiZhi {
	public static final PeiZhi INS = new PeiZhi();

	public boolean hudKai = false;        // 材料信息 HUD
	public boolean zhuGongKai = false;    // 辅助合成
	public boolean jianCeBanBen = true;   // 版本检测开关
	public int hudHangShu = 6;            // HUD 最多显示几行
	public int hudZaiSheng = 3;           // HUD 距屏幕左边 (百分比粗略值)
	public int zhongJianJu = 6;           // 辅助合成最多显示几步
	public int gengXinMiao = 3;           // 材料刷新间隔（秒）
	public int hudTouMing = 85;           // 背景透明度 0-255
	public boolean fuMoGuang = true;      // 图标附魔光效
	public String maidangFanWei = "full"; // 配置面板范围
	// 跟别的 HUD 撞上了就自己调这三个，比写死位置省事
	public int hudChatJianJu = 96;        // 左下给聊天框留的高度
	public int hudPianYiX = 0;            // 横着挪一挪（正数往左）
	public int hudPianYiY = 0;            // 完成提示上下挪一挪
	public boolean shouCeSeen = false;    // 使用手册是否已首次弹出过

	private static Path file() {
		return Paths.get("config", "linchi.json");
	}

	public void load() {
		try {
			Path p = file();
			if (!Files.exists(p)) { save(); return; }
			String txt = new String(Files.readAllBytes(p), StandardCharsets.UTF_8);
			JsonObject o = JsonParser.parseString(txt).getAsJsonObject();
			hudKai = bool(o, "hudKai", hudKai);
			zhuGongKai = bool(o, "zhuGongKai", zhuGongKai);
			jianCeBanBen = bool(o, "jianCeBanBen", jianCeBanBen);
			hudHangShu = num(o, "hudHangShu", hudHangShu);
			hudZaiSheng = num(o, "hudZaiSheng", hudZaiSheng);
			zhongJianJu = num(o, "zhongJianJu", zhongJianJu);
			gengXinMiao = num(o, "gengXinMiao", gengXinMiao);
			hudTouMing = num(o, "hudTouMing", hudTouMing);
			fuMoGuang = bool(o, "fuMoGuang", fuMoGuang);
			hudChatJianJu = num(o, "hudChatJianJu", hudChatJianJu);
			hudPianYiX = num(o, "hudPianYiX", hudPianYiX);
			hudPianYiY = num(o, "hudPianYiY", hudPianYiY);
			shouCeSeen = bool(o, "shouCeSeen", shouCeSeen);
		} catch (Exception e) {
			LinChiMod.LOG.warn("配置读取失败，用默认值", e);
		}
	}

	public void save() {
		try {
			Path p = file();
			Files.createDirectories(p.getParent());
			JsonObject o = new JsonObject();
			o.addProperty("hudKai", hudKai);
			o.addProperty("zhuGongKai", zhuGongKai);
			o.addProperty("jianCeBanBen", jianCeBanBen);
			o.addProperty("hudHangShu", hudHangShu);
			o.addProperty("hudZaiSheng", hudZaiSheng);
			o.addProperty("zhongJianJu", zhongJianJu);
			o.addProperty("gengXinMiao", gengXinMiao);
			o.addProperty("hudTouMing", hudTouMing);
			o.addProperty("fuMoGuang", fuMoGuang);
			o.addProperty("hudChatJianJu", hudChatJianJu);
			o.addProperty("hudPianYiX", hudPianYiX);
			o.addProperty("hudPianYiY", hudPianYiY);
			o.addProperty("shouCeSeen", shouCeSeen);
			Files.write(p, o.toString().getBytes(StandardCharsets.UTF_8));
		} catch (Exception e) {
			LinChiMod.LOG.warn("配置保存失败", e);
		}
	}

	static boolean bool(JsonObject o, String k, boolean def) {
		return o.has(k) ? o.get(k).getAsBoolean() : def;
	}
	static int num(JsonObject o, String k, int def) {
		return o.has(k) ? o.get(k).getAsInt() : def;
	}
}
