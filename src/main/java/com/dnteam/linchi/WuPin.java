package com.dnteam.linchi;

// 一个魔法物品条目
public class WuPin {
	public String ming;      // 中文名，唯一键
	public String mingLower; // 提前算好的小写名，搜索时别再 toLowerCase 了（那玩意每帧调用很亏）
	public String fenLei;    // 分类
	public String fenLeiLower; // 预计算的小写分类，搜索时别再逐条 toLowerCase
	public String base;      // 原版 base 小写枚举名，用来找图标
	public String huoQu;     // 获取途径文字
	public PeiFang gongFang; // 工作台配方
	public PeiFang moJing;   // 魔镜配方

	public boolean keHeCheng() {
		return gongFang != null || moJing != null;
	}
}
