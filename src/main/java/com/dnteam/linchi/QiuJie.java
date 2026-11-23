package com.dnteam.linchi;

import java.util.*;

// 递归展开：目标魔法物品 -> 基础材料表 + 每一步合成
// 逻辑照搬 Calculator 的 solve()，默认不拆原版物品
public class QiuJie {
	public static class Bu {          // 一个合成步骤
		public String ming;
		public PeiFang pf;
		public int times;             // 要做几轮
		public int need;              // 目标总需求
		public int level;             // 分层序号，用于步骤排序
	}
	public static class Cai {         // 一个基础材料
		public String key;
		public int count;
		public Cai(String k, int c) { key = k; count = c; }
	}

	public final LinkedHashMap<String, Cai> base = new LinkedHashMap<>();
	public final LinkedHashMap<String, Bu> crafts = new LinkedHashMap<>();
	public final List<String> notes = new ArrayList<>();
	private final LinkedHashMap<String, Integer> needTotal = new LinkedHashMap<>();
	private final Set<String> stack = new HashSet<>();

	public static QiuJie run(List<Target> targets) {
		QiuJie q = new QiuJie();
		for (Target t : targets) q.expand(t.ming, t.count);
		q.sortLevel();
		return q;
	}

	public static class Target {
		public String ming; public int count;
		public Target(String m, int c) { ming = m; count = c; }
	}

	private void addBase(String key, int n) {
		Cai b = base.get(key);
		if (b != null) b.count += n;
		else base.put(key, new Cai(key, n));
	}

	private boolean directGain(WuPin it) {
		if (it == null || it.huoQu == null) return false;
		String w = it.huoQu;
		return !w.contains("合成") && !w.isEmpty();
	}

	private void expand(String key, int need) {
		WuPin it = ShuJuKu.INS.get(key);
		if (it == null || !it.keHeCheng()) { addBase(key, need); return; }
		if (directGain(it)) { addBase(key, need); return; }
		if (stack.contains(key)) {
			if (!notes.contains(key)) notes.add(key);
			addBase(key, need);
			return;
		}
		needTotal.merge(key, need, Integer::sum);
		PeiFang pf = it.gongFang != null ? it.gongFang : it.moJing;
		int per = Math.max(1, pf.count);
		int times = (int) Math.ceil(needTotal.get(key) * 1.0 / per);
		Bu prev = crafts.get(key);
		int delta = times - (prev == null ? 0 : prev.times);
		if (prev != null) prev.need = needTotal.get(key);
		if (delta <= 0) return;
		Bu bu = new Bu();
		bu.ming = key; bu.pf = pf; bu.times = times; bu.need = needTotal.get(key);
		crafts.put(key, bu);
		stack.add(key);
		for (Map.Entry<String, Integer> e : pf.xuYao().entrySet()) expand(e.getKey(), e.getValue() * delta);
		stack.remove(key);
	}

	private void sortLevel() {
		for (Bu bu : crafts.values()) bu.level = levelOf(bu.ming, new HashSet<>());
		List<Bu> list = new ArrayList<>(crafts.values());
		list.sort(Comparator.comparingInt(b -> b.level));
		crafts.clear();
		for (Bu bu : list) crafts.put(bu.ming, bu);
	}

	private int levelOf(String key, Set<String> seen) {
		Bu bu = crafts.get(key);
		if (bu == null) return 0;
		if (seen.contains(key)) return 0;
		seen.add(key);
		int m = 0;
		for (String k : bu.pf.xuYao().keySet()) m = Math.max(m, levelOf(k, seen));
		seen.remove(key);
		return m + 1;
	}
}
