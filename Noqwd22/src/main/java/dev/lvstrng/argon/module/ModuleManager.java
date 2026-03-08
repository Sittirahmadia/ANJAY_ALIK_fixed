package dev.lvstrng.argon.module;

import dev.lvstrng.argon.Argon;
import dev.lvstrng.argon.event.events.ButtonListener;
import dev.lvstrng.argon.module.modules.client.ClickGUI;
import dev.lvstrng.argon.module.modules.client.Friends;
import dev.lvstrng.argon.module.modules.client.SelfDestruct;
import dev.lvstrng.argon.module.modules.combat.*;
import dev.lvstrng.argon.module.modules.misc.*;
import dev.lvstrng.argon.module.modules.render.*;
import dev.lvstrng.argon.module.setting.KeybindSetting;
import dev.lvstrng.argon.utils.EncryptedString;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public final class ModuleManager implements ButtonListener {
	private final List<Module> modules = new ArrayList<>();

	public ModuleManager() {
		addModules();
		addKeybinds();
	}

	public void addModules() {
		// Combat
		add(new AutoCrystal());
		add(new AnchorMacro());
		add(new AutoDtap());
		add(new AimAssist());
		add(new Hitboxes());
		add(new TriggerBot());
		add(new TriggerBotV2());
		add(new MaceSwap());
		add(new AutoDoubleHand());
		add(new AutoHitCrystal());
		add(new AutoInventoryTotem());
		add(new HitCob());
		add(new WebMacro());
		add(new HoverTotem());
		add(new ShieldDisabler());
		add(new TotemOffhand());
		add(new AutoJumpReset());

		// Misc
		add(new TriggerBotV3());
		add(new PearlCatch());
		add(new WindChargeKey());
		add(new Prevent());
		add(new AutoXP());
		add(new KeyPearl());
		add(new Freecam());
		add(new PackSpoof());
		add(new Sprint());

		// Render
		add(new HUD());
		add(new NoBounce());
		add(new PlayerESP());
		add(new StorageEsp());
		add(new TargetHud());

		// Client
		add(new ClickGUI());
		add(new Friends());
		add(new SelfDestruct());
	}

	public List<Module> getEnabledModules() {
		return modules.stream().filter(Module::isEnabled).toList();
	}

	public List<Module> getModules() {
		return modules;
	}

	public void addKeybinds() {
		Argon.INSTANCE.getEventManager().add(ButtonListener.class, this);
		for (Module module : modules)
			module.addSetting(new KeybindSetting(
					EncryptedString.of("Keybind"), module.getKey(), true)
					.setDescription(EncryptedString.of("Key to enable the module")));
	}

	public List<Module> getModulesInCategory(Category category) {
		return modules.stream()
				.filter(module -> module.getCategory() == category)
				.toList();
	}

	@SuppressWarnings("unchecked")
	public <T extends Module> T getModule(Class<T> moduleClass) {
		return (T) modules.stream()
				.filter(moduleClass::isInstance)
				.findFirst()
				.orElse(null);
	}

	public void add(Module module) {
		modules.add(module);
	}

	@Override
	public void onButtonPress(ButtonEvent event) {
		if (!SelfDestruct.destruct) {
			modules.forEach(module -> {
				if (module.getKey() == event.button && event.action == GLFW.GLFW_PRESS)
					module.toggle();
			});
		}
	}
}
