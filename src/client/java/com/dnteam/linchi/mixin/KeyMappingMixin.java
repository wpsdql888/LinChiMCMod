package com.dnteam.linchi.mixin;

import com.dnteam.linchi.JianRong;
import com.dnteam.linchi.LinChiClient;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// 原版 P 键默认绑定“社交菜单”。默认 CTRL+P 打开配置面板时，原版那套也会触发，
// 社交菜单会闪一下、甚至盖掉配置面板。这里在 consumeClick 层面把社交按键按住
// CTRL 的那次消费压掉（同时清零 clickCount，防止松键后补弹）。
// 只有我们的配置面板仍绑定在 P 上时才压制；玩家改绑别的键就不动原版行为。
@Mixin(KeyMapping.class)
public class KeyMappingMixin {
	@Inject(method = "consumeClick", at = @At("HEAD"), cancellable = true)
	private void linchi$suppressSocialOnCtrlP(CallbackInfoReturnable<Boolean> cir) {
		try {
			if (LinChiClient.keyPeiZhi == null) return;
			if (!JianRong.ctrlDown()) return;
			if (JianRong.boundKeyValue(LinChiClient.keyPeiZhi) != InputConstants.KEY_P) return;
			Minecraft mc = Minecraft.getInstance();
			if (mc.options == null) return;
			Object social = mc.options.getClass().getField("keySocialInteractions").get(mc.options);
			if (social == (Object) this) {
				JianRong.resetClickCount((KeyMapping) (Object) this);
				cir.setReturnValue(false);
			}
		} catch (Throwable ignored) {}
	}
}
