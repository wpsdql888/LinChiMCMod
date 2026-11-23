package com.dnteam.linchi;

import java.util.*;

// 一条配方：形状合成或魔镜九宫格都塞这里
public class PeiFang {
	public int count = 1;
	public List<String> shape = new ArrayList<>();
	public Map<String, String> mat = new HashMap<>();
	public boolean shapeless = false;
	public List<String> ing = new ArrayList<>();
	public boolean mirror = false;

	// 配方对象在数据加载完就是只读的，摊平结果和用量统计都缓存下来。
	// 递归求解、悬停提示、材料板渲染都会反复要这两份数据，之前每次都新建
	// 一个 9 元素的 ArrayList + 一个 Map，纯属浪费。
	private List<String> gridCache;
	private Map<String, Integer> needCache;

	// 摊平成 9 格。工作台用 shape+mat，魔镜直接 9 槽
	public List<String> jiuGe() {
		if (gridCache != null) return gridCache;
		ArrayList<String> grid = new ArrayList<>();
		for (int i = 0; i < 9; i++) grid.add(null);
		if (mirror) {
			for (int i = 0; i < Math.min(9, ing.size()); i++) grid.set(i, ing.get(i));
			gridCache = grid;
			return grid;
		}
		if (shapeless) {
			for (int i = 0; i < Math.min(9, ing.size()); i++) grid.set(i, ing.get(i));
			gridCache = grid;
			return grid;
		}
		for (int y = 0; y < shape.size() && y < 3; y++) {
			String line = shape.get(y);
			if (line == null) continue;
			String[] chs = line.split("");
			for (int x = 0; x < chs.length && x < 3; x++) {
				String who = mat.get(chs[x]);
				if (who != null) grid.set(y * 3 + x, who);
			}
		}
		gridCache = grid;
		return grid;
	}

	// 统计每种材料要几个
	public Map<String, Integer> xuYao() {
		if (needCache != null) return needCache;
		LinkedHashMap<String, Integer> cnt = new LinkedHashMap<>();
		for (String g : jiuGe()) {
			if (g == null) continue;
			cnt.merge(g, 1, Integer::sum);
		}
		needCache = cnt;
		return cnt;
	}
}
