package com.dnteam.linchi.mixin;

import com.dnteam.linchi.gui.GuiTool;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// 任何界面被移走时都把“有界面打开”的标志清掉
// 不然玩家开一次箱子再关掉，HUD 会一直以为还在界面里，再也不显示
@Mixin(Screen.class)
public class ScreenLifecycleMixin {
	@Inject(method = "removed", at = @At("HEAD"))
	void linchiScreenRemoved(CallbackInfo ci) {
		GuiTool.setOpen(false);
	}
}
