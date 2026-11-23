package com.dnteam.linchi.mixin;

import com.dnteam.linchi.GongFangZhiYin;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.CraftingScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// 只负责在工作台背景上画那几行指引
// 鼠标交互走 Fabric 的 ScreenMouseEvents，见 HudRender.register()
// CraftingScreen 自己没声明 mouseClicked / mouseScrolled，往它身上注入会在运行时炸
@Mixin(CraftingScreen.class)
public class CraftingScreenMixin {
	@Inject(method = "extractBackground", at = @At("TAIL"))
	void linchiDraw(GuiGraphicsExtractor g, int mx, int my, float delta, CallbackInfo ci) {
		CraftingScreen self = (CraftingScreen)(Object)this;
		GongFangZhiYin.draw(g, self, mx, my);
	}
}
