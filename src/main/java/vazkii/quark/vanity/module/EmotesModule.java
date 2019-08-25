package vazkii.quark.vanity.module;

import java.io.File;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.mojang.blaze3d.platform.GlStateManager;

import net.minecraft.client.MainWindow;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.button.Button;
import net.minecraft.client.renderer.entity.model.BipedModel;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.resources.ResourcePackInfo;
import net.minecraft.resources.ResourcePackInfo.IFactory;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent.ElementType;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.event.TickEvent.RenderTickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import vazkii.aurelienribon.tweenengine.Tween;
import vazkii.quark.base.handler.ContributorRewardHandler;
import vazkii.quark.base.module.Config;
import vazkii.quark.base.module.LoadModule;
import vazkii.quark.base.module.Module;
import vazkii.quark.base.module.ModuleCategory;
import vazkii.quark.base.network.QuarkNetwork;
import vazkii.quark.base.network.message.RequestEmoteMessage;
import vazkii.quark.vanity.client.emote.CustomEmoteIconResourcePack;
import vazkii.quark.vanity.client.emote.EmoteBase;
import vazkii.quark.vanity.client.emote.EmoteDescriptor;
import vazkii.quark.vanity.client.emote.EmoteHandler;
import vazkii.quark.vanity.client.emote.ModelAccessor;
import vazkii.quark.vanity.client.gui.EmoteButton;
import vazkii.quark.vanity.client.gui.TranslucentButton;

@LoadModule(category = ModuleCategory.VANITY, hasSubscriptions = true, subscribeOn = Dist.CLIENT)
public class EmotesModule extends Module {

	private static final Set<String> DEFAULT_EMOTE_NAMES = ImmutableSet.of(
			"no",
			"yes",
			"wave", 
			"salute", 
			"cheer",
			"clap", 
			"think",
			"point", 
			"shrug",
			"headbang",
			"weep",
			"facepalm"
	);

	private static final Set<String> PATREON_EMOTES = ImmutableSet.of(
			"dance", 
			"tpose", 
			"dab",
			"jet",
			"exorcist",
			"zombie"
			);

	public static final int EMOTE_BUTTON_WIDTH = 25;
	public static final int EMOTES_PER_ROW = 3;

	@Config(description = "The enabled default emotes. Remove from this list to disable them. You can also re-order them, if you feel like it.")
			public static List<String> enabledEmotes = Lists.newArrayList(DEFAULT_EMOTE_NAMES);
	
	@Config(description = "The list of Custom Emotes to be loaded.\nWatch the tutorial on Custom Emotes to learn how to make your own: https://youtu.be/ourHUkan6aQ") 
	public static List<String> customEmotes = Lists.newArrayList();
	
	@Config(description = "Enable this to make custom emotes read the file every time they're triggered so you can edit on the fly.\nDO NOT ship enabled this in a modpack, please.")
	public static boolean customEmoteDebug = false;
	
	public static boolean emotesVisible = false;
	public static File emotesDir;
	
	@OnlyIn(Dist.CLIENT)
	public static CustomEmoteIconResourcePack resourcePack;

	@OnlyIn(Dist.CLIENT) 
	public static <T extends ResourcePackInfo> void addResourcePack(Map<String, T> nameToPackMap, IFactory<T> packInfoFactory) {
		resourcePack = new CustomEmoteIconResourcePack();
		
		String name = "quark:emote_resources";
		T t = ResourcePackInfo.createResourcePack(name, true, () -> resourcePack, packInfoFactory, ResourcePackInfo.Priority.TOP);
		nameToPackMap.put(name, t);
	}
	
	@Override
	@OnlyIn(Dist.CLIENT)
	public void clientSetup() {
		Tween.registerAccessor(BipedModel.class, ModelAccessor.INSTANCE);

		for(String s : enabledEmotes)
			if(DEFAULT_EMOTE_NAMES.contains(s))
				EmoteHandler.addEmote(s);

		for(String s : PATREON_EMOTES)
			EmoteHandler.addEmote(s);
		
		for(String s : customEmotes)
			EmoteHandler.addCustomEmote(s);

//		if(enableKeybinds)
//			ModKeybinds.initEmoteKeybinds();
	}

	@SubscribeEvent
	@OnlyIn(Dist.CLIENT)
	public void initGui(GuiScreenEvent.InitGuiEvent.Post event) {
		Screen gui = event.getGui();
		if(gui instanceof ChatScreen) {
			Map<Integer, List<EmoteDescriptor>> descriptorSorting = new TreeMap<>();

			for (EmoteDescriptor desc : EmoteHandler.emoteMap.values()) {
				if (desc.getTier() <= ContributorRewardHandler.localPatronTier) {
					List<EmoteDescriptor> descriptors = descriptorSorting.get(desc.getTier());
					if (descriptors == null)
						descriptorSorting.put(desc.getTier(), descriptors = new LinkedList<>());

					descriptors.add(desc);
				}
			}

			int rows = 0;
			int row = 0;
			int tierRow, rowPos;

			Set<Integer> keys = descriptorSorting.keySet();
			for(int tier : keys) {
				List<EmoteDescriptor> descriptors = descriptorSorting.get(tier);
				if (descriptors != null) {
					rows += descriptors.size() / 3;
					if (descriptors.size() % 3 != 0)
						rows++;
				}
			}

			List<Button> emoteButtons = new LinkedList<>();
			for (int tier : keys) {
				rowPos = 0;
				tierRow = 0;
				List<EmoteDescriptor> descriptors = descriptorSorting.get(tier);
				if (descriptors != null) {
					for (EmoteDescriptor desc : descriptors) {
						int rowSize = Math.min(descriptors.size() - tierRow * EMOTES_PER_ROW, EMOTES_PER_ROW);

						int x = gui.width - (((rowPos + 1) * 2 + EMOTES_PER_ROW - rowSize) * EMOTE_BUTTON_WIDTH / 2 + 1);
						int y = gui.height - (40 + EMOTE_BUTTON_WIDTH * (rows - row));

						Button button = new EmoteButton(x, y, desc, (b) -> {
							String name = desc.getRegistryName();
							QuarkNetwork.sendToServer(new RequestEmoteMessage(name));
						});
						emoteButtons.add(button);
						
						button.visible = emotesVisible;
						button.active = emotesVisible;
						event.addWidget(button);

						if (++rowPos == EMOTES_PER_ROW) {
							tierRow++;
							row++;
							rowPos = 0;
						}
					}
				}
				if (rowPos != 0)
					row++;
			}
			
			event.addWidget(new TranslucentButton(gui.width - 1 - EMOTE_BUTTON_WIDTH * EMOTES_PER_ROW, gui.height - 40, EMOTE_BUTTON_WIDTH * EMOTES_PER_ROW, 20, I18n.format("quark.gui.button.emotes"),
					(b) -> {
						for(Button bt : emoteButtons)
							if(bt instanceof EmoteButton) {
								bt.visible = !bt.visible;
								bt.active = !bt.active;
							}

						emotesVisible = !emotesVisible;
					}));
		}
	}

	@SubscribeEvent
	@OnlyIn(Dist.CLIENT)
	public void drawHUD(RenderGameOverlayEvent.Post event) {
		if(event.getType() == ElementType.ALL) {
			Minecraft mc = Minecraft.getInstance();
			MainWindow res = event.getWindow();
			EmoteBase emote = EmoteHandler.getPlayerEmote(mc.player);
			if(emote != null && emote.timeDone < emote.totalTime) {
				ResourceLocation resource = emote.desc.texture;
				int x = res.getScaledWidth() / 2 - 16;
				int y = res.getScaledHeight() / 2 - 60;
				float transparency = 1F;
				float tween = 5F;

				if(emote.timeDone < tween)
					transparency = emote.timeDone / tween;
				else if(emote.timeDone > emote.totalTime - tween)
					transparency = (emote.totalTime - emote.timeDone) / tween;

				GlStateManager.pushMatrix();
				GlStateManager.disableLighting();
				GlStateManager.enableBlend();
				GlStateManager.disableAlphaTest();

				GlStateManager.color4f(1F, 1F, 1F, transparency);
				mc.getTextureManager().bindTexture(resource);
				Screen.blit(x, y, 0, 0, 32, 32, 32, 32);
				GlStateManager.enableBlend();

				String name = I18n.format(emote.desc.getTranslationKey());
				mc.fontRenderer.drawStringWithShadow(name, res.getScaledWidth() / 2f - mc.fontRenderer.getStringWidth(name) / 2f, y + 34, 0xFFFFFF + (((int) (transparency * 255F)) << 24));
				GlStateManager.popMatrix();
			}
		}
	}	


	@SubscribeEvent
	@OnlyIn(Dist.CLIENT)
	public void renderTick(RenderTickEvent event) {
		EmoteHandler.onRenderTick(Minecraft.getInstance());
	}

	@SubscribeEvent(priority = EventPriority.HIGHEST)
	@OnlyIn(Dist.CLIENT)
	public void preRenderLiving(RenderLivingEvent.Pre<PlayerEntity, ?> event) {
		if(event.getEntity() instanceof PlayerEntity)
			EmoteHandler.preRender((PlayerEntity) event.getEntity());
	}

	@SubscribeEvent(priority = EventPriority.LOWEST)
	@OnlyIn(Dist.CLIENT)
	public void postRenderLiving(RenderLivingEvent.Post<PlayerEntity, ?> event) {
		if(event.getEntity() instanceof PlayerEntity)
			EmoteHandler.postRender((PlayerEntity) event.getEntity());
	}


}