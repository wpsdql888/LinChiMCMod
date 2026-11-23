package com.dnteam.linchi;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LinChiMod implements ModInitializer {
	public static final String MOD_ID = "linchi";
	public static final Logger LOG = LoggerFactory.getLogger("LinChi");

	@Override
	public void onInitialize() {
		ShuJuKu.INS.load();
		PeiZhi.INS.load();
		LOG.info("LinChiMCMod 1.0.0 初始化完成 - by wpsdql888 / DNT Group");
	}
}
