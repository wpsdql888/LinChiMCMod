package com.dnteam.linchi;

import java.util.*;

// 全局运行状态：目标清单 + 定时算好的材料/步骤，HUD 直接读
// 每帧只做“读缓存 → 画”，扫描背包和重算都放到定时 tick 里
public class ZhuangTai {
	public static final ZhuangTai INS = new ZhuangTai();

	public final List<QiuJie.Target> targets = new ArrayList<>();
	public BeiBao.JiShu jiShu = new BeiBao.JiShu();
	public QiuJie latest = new QiuJie();
	public long lastScan = 0;

	// 这两份是 HUD / 工作台共用的展示行，重建只在数据变动时发生
	private final ArrayList<CaiRow> hudCache = new ArrayList<>();
	private final ArrayList<BuRow> craftCache = new ArrayList<>();
	private boolean doneCache = false;
	// 数据每次 rebuild 都 +1，供外部缓存判断“列表变没变”。
	// 只比对列表实例是不行的：craftCache 是同一个对象，clear 后重填引用不变。
	private int version = 0;
	public int version() { return version; }

	public void setTargets(List<QiuJie.Target> ts) {
		targets.clear();
		for (QiuJie.Target t : ts) targets.add(new QiuJie.Target(t.ming, t.count));
		recalc();
	}

	public void recalc() {
		latest = QiuJie.run(targets);
		rebuild();
	}

	// 每 3 秒扫一次背包（间隔可在配置里改）
	public void tick() {
		long now = System.currentTimeMillis();
		// 系统时间被往回调时 now < lastScan，差值变负数会让刷新永久停摆。
		// 检测到回拨就把基准清零，下一帧立刻重扫一次。
		if (now < lastScan) lastScan = 0;
		int sec = PeiZhi.INS.gengXinMiao;
		if (sec < 1) sec = 1; // 配置异常时兜底，别每帧全量扫背包
		if (now - lastScan < sec * 1000L) return;
		lastScan = now;
		jiShu = BeiBao.scan();
		recalc();
	}

	// 把配方结果 + 背包数量合成展示行，只在这里分配对象
	private void rebuild() {
		hudCache.clear();
		for (QiuJie.Cai c : latest.base.values()) {
			// 材料 key 可能是 minecraft:xxx，也可能是魔法物品中文名；
			// 统一翻成显示名再去背包里数，免得把别的物品算进来。
			int que = Math.max(0, c.count - jiShu.mingYou(TuBiao.mingOf(c.key)));
			if (que > 0) {
				CaiRow r = new CaiRow();
				r.key = c.key; r.need = c.count; r.que = que;
				hudCache.add(r);
			}
		}
		hudCache.trimToSize();
		doneCache = !targets.isEmpty() && hudCache.isEmpty();

		craftCache.clear();
		for (QiuJie.Bu bu : latest.crafts.values()) {
			int owned = ownedOf(bu.ming);
			int remain = Math.max(0, bu.need - owned);
			if (remain > 0) {
				BuRow r = new BuRow();
				r.bu = bu; r.remain = remain;
				craftCache.add(r);
			}
		}
		craftCache.trimToSize();
		version++;
	}

	// 魔法物品中文名 -> 背包里同名物品的拥有数量
	private int ownedOf(String ming) {
		return jiShu.mingYou(ming);
	}

	public static class CaiRow {
		public String key; public int need; public int que;
	}
	public List<CaiRow> hudRows() { return hudCache; }

	public boolean allDone() { return doneCache; }

	public static class BuRow {
		public QiuJie.Bu bu; public int remain;
	}
	public List<BuRow> craftRows() { return craftCache; }
}
